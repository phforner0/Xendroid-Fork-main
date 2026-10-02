#!/usr/bin/env python3
"""Static check of the JNI bindings between emulator-core's Java and C++ (plan A08).

A RegisterNatives table entry that matches no Java `native` method makes the whole
call fail when the library loads, so the app cannot start; a Java `native` method
that nothing registers or exports throws UnsatisfiedLinkError on its first call;
a C++ parameter of the wrong primitive type corrupts arguments silently. None of
that shows up before the app runs on a device, so this script compares:

  * every Java `native` declaration (with its JNI signature, inheritance-aware:
    ART's RegisterNatives also finds a method declared in a superclass),
  * every `JNINativeMethod` table entry and the class its RegisterNatives targets,
  * every exported `Java_...` function,
  * the C++ parameter/return types of each registered function against the signature.

Usage: python3 tools/check-jni.py [repo-root]. Exit status 1 when something is wrong.
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
JAVA_ROOT = ROOT / "emulator-core/src/main/java"
CPP_ROOT = ROOT / "emulator-core/src/main/cpp"

PRIMITIVES = {"boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I",
              "long": "J", "float": "F", "double": "D", "void": "V"}
JAVA_LANG = {"String", "Object", "Class", "Throwable", "Integer", "Long", "Boolean"}


def strip_java(text):
    """Comments and string/char literals out, positions kept roughly (newlines kept)."""
    pattern = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
    return pattern.sub(lambda m: "\n" * m.group(0).count("\n") if m.group(0).startswith("/") else '""', text)


def strip_cpp_comments(text):
    """C++ comments out, string literals kept (the tables are made of them)."""
    pattern = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', re.S)
    return pattern.sub(lambda m: m.group(0) if m.group(0).startswith('"') else "\n" * m.group(0).count("\n"), text)


class JavaFile:
    def __init__(self, path):
        self.path = path
        raw = path.read_text(encoding="utf-8")
        self.text = strip_java(raw)
        self.package = (re.search(r"\bpackage\s+([\w.]+)\s*;", self.text) or [None, ""])[1]
        self.imports = {m.group(1).rsplit(".", 1)[-1]: m.group(1)
                        for m in re.finditer(r"\bimport\s+([\w.]+)\s*;", self.text)}
        self.classes = {}      # binary name -> declared superclass as written (or None)
        self.nested = {}       # simple name -> binary name, for classes declared in this file
        self.natives = []      # (binary class, name, [param types as written], return type as written, line)
        self._scan()

    def _scan(self):
        token = re.compile(r"\b(class|interface|enum)\s+(\w+)(?:\s*<[^>]*>)?(?:\s+extends\s+([\w.]+))?|[{};]")
        stack = []             # (binary name, depth at which its body opened)
        pending = None
        depth = 0
        statement_start = 0
        for m in token.finditer(self.text):
            if m.group(1):
                outer = stack[-1][0] if stack else None
                binary = f"{outer}${m.group(2)}" if outer else f"{self.package.replace('.', '/')}/{m.group(2)}"
                pending = (binary, m.group(3))
                self.classes[binary] = m.group(3)
                self.nested[m.group(2)] = binary
                continue
            symbol = m.group(0)
            if symbol == "{":
                depth += 1
                if pending:
                    stack.append((pending[0], depth))
                    pending = None
            elif symbol == "}":
                if stack and stack[-1][1] == depth:
                    stack.pop()
                depth -= 1
            elif symbol == ";":
                statement = self.text[statement_start:m.start()]
                if re.search(r"\bnative\b", statement) and stack:
                    decl = re.search(r"([\w.$\[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[\w.,\s]+)?$",
                                     statement.strip(), re.S)
                    if decl:
                        params = [p.strip() for p in decl.group(3).split(",") if p.strip()]
                        types = [re.sub(r"\b(final)\b|@\w+", "", p).strip().rsplit(None, 1)[0] for p in params]
                        line = self.text.count("\n", 0, m.start()) + 1
                        self.natives.append((stack[-1][0], decl.group(2), types, decl.group(1), line))
            if symbol in "{};":
                statement_start = m.end()

    def resolve(self, written):
        """Type as written in this file -> JNI descriptor."""
        dims = written.count("[]") + (1 if written.endswith("...") else 0)
        base = written.replace("[]", "").replace("...", "").strip()
        if base in PRIMITIVES:
            descriptor = PRIMITIVES[base]
        else:
            first, _, rest = base.partition(".")
            if first in self.nested:
                binary = self.nested[first]
            elif first in self.imports:
                binary = self.imports[first].replace(".", "/")
            elif first in JAVA_LANG:
                binary = f"java/lang/{first}"
            elif first[:1].islower():           # fully qualified, e.g. android.view.Surface
                parts = base.split(".")
                package = [p for p in parts if p[:1].islower()]
                classes = [p for p in parts if not p[:1].islower()]
                binary, rest = "/".join(package + classes[:1]), ".".join(classes[1:])
            else:
                binary = f"{self.package.replace('.', '/')}/{first}"
            if rest:
                binary += "$" + rest.replace(".", "$")
            descriptor = f"L{binary};"
        return "[" * dims + descriptor


def method_signature(java, types, ret):
    return "(" + "".join(java.resolve(t) for t in types) + ")" + java.resolve(ret)


def decode_jni_mangled(name):
    out, i = [], 0
    while i < len(name):
        c = name[i]
        if c == "_" and i + 1 < len(name):
            n = name[i + 1]
            if n == "1":
                out.append("_"); i += 2; continue
            if n == "2":
                out.append(";"); i += 2; continue
            if n == "3":
                out.append("["); i += 2; continue
            if n == "0":
                out.append(chr(int(name[i + 2:i + 6], 16))); i += 6; continue
            if n == "_":
                break                       # overloaded signature follows
        out.append("/" if c == "_" else c)
        i += 1
    path = "".join(out)
    cls, _, method = path.rpartition("/")
    return cls, method


C_TYPES = {"Z": {"jboolean"}, "B": {"jbyte"}, "C": {"jchar"}, "S": {"jshort"}, "I": {"jint"},
           "J": {"jlong"}, "F": {"jfloat"}, "D": {"jdouble"}, "V": {"void"}}


def expected_c_types(descriptor):
    if descriptor in C_TYPES:
        return C_TYPES[descriptor]
    if descriptor.startswith("["):
        element = descriptor[1:]
        if element in C_TYPES and element != "V":
            return {f"{next(iter(C_TYPES[element]))}Array", "jarray", "jobject"}
        return {"jobjectArray", "jarray", "jobject"}
    if descriptor == "Ljava/lang/String;":
        return {"jstring", "jobject"}
    if descriptor == "Ljava/lang/Class;":
        return {"jclass", "jobject"}
    return {"jobject"}


def split_descriptor(signature):
    params, i = [], 1
    while signature[i] != ")":
        start = i
        while signature[i] == "[":
            i += 1
        if signature[i] == "L":
            i = signature.index(";", i)
        i += 1
        params.append(signature[start:i])
    return params, signature[i + 1:]


def main():
    problems = []
    javas = [JavaFile(p) for p in sorted(JAVA_ROOT.rglob("*.java"))]
    supers = {}
    natives = {}                            # (class, name, signature) -> where
    for java in javas:
        for binary, written in java.classes.items():
            supers[binary] = java.resolve(written)[1:-1] if written else None
        for binary, name, types, ret, line in java.natives:
            natives[(binary, name, method_signature(java, types, ret))] = f"{java.path.relative_to(ROOT)}:{line}"

    def lineage(binary):
        while binary:
            yield binary
            binary = supers.get(binary)

    registrations, exported, functions = [], set(), {}
    for cpp in sorted(CPP_ROOT.glob("*.cpp")):
        text = strip_cpp_comments(cpp.read_text(encoding="utf-8", errors="replace"))
        for m in re.finditer(r"\b(?:static\s+)?(?:const\s+)?([\w:]+(?:\s*\*)?)\s+(\w+)\s*\(\s*JNIEnv\s*\*\s*\w*\s*,"
                             r"\s*(jobject|jclass)\s*\w*\s*((?:,[^)]*)?)\)\s*\{", text):
            params = [re.sub(r"\bconst\b", "", p).strip().rsplit(None, 1)[0].replace(" ", "")
                      for p in m.group(4).split(",") if p.strip()]
            functions[m.group(2)] = (m.group(1).replace(" ", ""), params,
                                     f"{cpp.relative_to(ROOT)}:{text.count(chr(10), 0, m.start()) + 1}")
        for m in re.finditer(r"\bJava_(\w+)\s*\(", text):
            exported.add(decode_jni_mangled(m.group(1)))
        for table in re.finditer(r"JNINativeMethod\s+\w+\s*\[\s*\]\s*=\s*\{(.*?)\};", text, re.S):
            # The registering function (it takes only JNIEnv*) up to its RegisterNatives
            # statement holds the FindClass of the target class.
            headers = list(re.finditer(r"\w+\s*\(\s*JNIEnv\s*\*\s*\w*\s*\)\s*\{", text[:table.start()]))
            func_start = headers[-1].start() if headers else 0
            call = text.find("RegisterNatives(", table.end())
            region_end = text.find(";", call) if call >= 0 else table.end()
            classes = set(re.findall(r'FindClass\(\s*"([^"]+)"\s*\)', text[func_start:region_end]))
            where = f"{cpp.relative_to(ROOT)}:{text.count(chr(10), 0, table.start()) + 1}"
            if len(classes) != 1:
                problems.append(f"{where}: cannot tell which class this table registers ({sorted(classes) or 'no FindClass'})")
                continue
            target = classes.pop()
            for e in re.finditer(r'\{\s*"(\w+)"\s*,\s*"([^"]+)"\s*,\s*\(\s*void\s*\*\s*\)\s*&?\s*(\w+)\s*\}', table.group(1)):
                registrations.append((target, e.group(1), e.group(2), e.group(3), where))

    # A native pointer travelling as a Java long handle is the same 64-bit register on
    # arm64-v8a, the only ABI this app builds; it would truncate on a 32-bit ABI.
    handles = []

    def compatible(c_type, descriptor, fn):
        if c_type in expected_c_types(descriptor):
            return True
        if descriptor == "J" and c_type.endswith("*"):
            handles.append(fn)
            return True
        return False

    seen = {}
    covered = set()
    for target, name, signature, fn, where in registrations:
        key = (target, name, signature)
        if key in seen:
            problems.append(f"{where}: {target}.{name}{signature} registered twice (also {seen[key]})")
        seen[key] = where
        owner = next((c for c in lineage(target) if (c, name, signature) in natives), None)
        if owner is None:
            problems.append(f"{where}: {target}.{name}{signature} matches no Java native method: "
                            "RegisterNatives fails and the whole class is left unbound")
            continue
        covered.add((owner, name, signature))
        if fn in functions:
            ret, params, defined = functions[fn]
            descriptors, ret_descriptor = split_descriptor(signature)
            if len(params) != len(descriptors):
                problems.append(f"{defined}: {fn} takes {len(params)} argument(s), {name}{signature} passes {len(descriptors)}")
            for i, (c_type, descriptor) in enumerate(zip(params, descriptors)):
                if not compatible(c_type, descriptor, fn):
                    problems.append(f"{defined}: {fn} argument {i + 1} is {c_type}, {name}{signature} passes {descriptor}")
            if not compatible(ret, ret_descriptor, fn):
                problems.append(f"{defined}: {fn} returns {ret}, {name}{signature} expects {ret_descriptor}")
    for (binary, name, signature), where in sorted(natives.items()):
        if (binary, name, signature) in covered or (binary, name) in exported:
            continue
        problems.append(f"{where}: native {binary.replace('/', '.')}.{name}{signature} is neither registered "
                        "nor exported: UnsatisfiedLinkError on its first call")

    if problems:
        for p in problems:
            print("JNI:", p)
        print(f"check-jni: {len(problems)} problem(s)")
        return 1
    print(f"check-jni: {len(natives)} Java natives, {len(registrations)} registrations, "
          f"{len(exported)} exported symbols: consistent")
    if handles:
        print(f"check-jni: note: {len(set(handles))} function(s) pass native pointers as Java long handles "
              f"({', '.join(sorted(set(handles)))}); fine on arm64-v8a only")
    return 0


if __name__ == "__main__":
    sys.exit(main())
