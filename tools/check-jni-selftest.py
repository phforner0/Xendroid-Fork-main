#!/usr/bin/env python3
"""Self-test of tools/check-jni.py on small generated trees: a consistent one must
pass and each planted binding error must be reported. No Android build needed."""
import subprocess
import sys
import tempfile
from pathlib import Path

CHECKER = Path(__file__).resolve().parent / "check-jni.py"

BASE_JAVA = """package demo.base;
import android.view.Surface;
public class Core {
    public static class Handle { public int fd; }
    public native void attach(Surface surface);
    public native void open(Core.Handle handle);
    public native int limit();   // registered on the subclass, like set_framerate_limit
}
"""

APP_JAVA = """package demo.app;
public class Bridge extends demo.base.Core {
    /** Comment with a fake declaration: public native void ghost(); */
    public static class Info { public String name; }
    public native long[] stats();
    public native Info[] list(String root, int kind);
    public native void submit(long id, boolean ok, String text) throws java.io.IOException;
    public static native String probe();
}
"""

GOOD_CPP = """#include <jni.h>
static void j_attach(JNIEnv* env, jobject self, jobject surface) {}
static void j_open(JNIEnv* env, jobject self, jobject handle) {}
static jint j_limit(JNIEnv* env, jobject self) { return 0; }
static jlongArray j_stats(JNIEnv* env, jobject self) { return nullptr; }
static jobjectArray j_list(JNIEnv* env, jobject self, jstring root, jint kind) { return nullptr; }
static void j_submit(JNIEnv* env, jobject self, jlong id, jboolean ok, jstring text) {}
extern "C" JNIEXPORT jstring JNICALL Java_demo_app_Bridge_probe(JNIEnv* env, jclass cls) { return nullptr; }

int register_core(JNIEnv* env){
    static const JNINativeMethod methods[] = {
        { "attach", "(Landroid/view/Surface;)V", (void *) j_attach },
        { "open", "(Ldemo/base/Core$Handle;)V", (void *) j_open },
    };
    return env->RegisterNatives(env->FindClass("demo/base/Core"), methods, 2);
}

int register_bridge(JNIEnv* env){
    jclass cls = env->FindClass("demo/app/Bridge");
    static const JNINativeMethod methods[] = {
        {"stats", "()[J", (void *) j_stats}
        ,{"list", "(Ljava/lang/String;I)[Ldemo/app/Bridge$Info;", (void *) j_list}
        // inherited from Core
        ,{"limit", "()I", (void *) j_limit}
        ,{"submit", "(JZLjava/lang/String;)V", (void *) j_submit}
    };
    return env->RegisterNatives(cls, methods, 4);
}
"""

BROKEN = {
    "registration without a Java native": ("{\"stats\", \"()[J\"", "{\"stats\", \"()[I\""),
    "Java native without registration": (",{\"limit\", \"()I\", (void *) j_limit}", ""),
    "wrong primitive argument": ("jlong id, jboolean ok", "jint id, jboolean ok"),
    "wrong return type": ("static jint j_limit", "static jlong j_limit"),
    "missing argument": ("jstring root, jint kind", "jstring root"),
    "duplicate registration": (",{\"limit\", \"()I\", (void *) j_limit}",
                               ",{\"limit\", \"()I\", (void *) j_limit}\n        ,{\"limit\", \"()I\", (void *) j_limit}"),
    "exported symbol for another method": ("Java_demo_app_Bridge_probe", "Java_demo_app_Bridge_probe_1old"),
}


def run(tree_cpp):
    with tempfile.TemporaryDirectory() as root:
        root = Path(root)
        for rel, text in (("emulator-core/src/main/java/demo/base/Core.java", BASE_JAVA),
                          ("emulator-core/src/main/java/demo/app/Bridge.java", APP_JAVA),
                          ("emulator-core/src/main/cpp/bridge.cpp", tree_cpp)):
            path = root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        result = subprocess.run([sys.executable, str(CHECKER), str(root)], capture_output=True, text=True)
        return result.returncode, result.stdout


def main():
    failures = []
    code, out = run(GOOD_CPP)
    if code != 0:
        failures.append(f"consistent tree reported problems:\n{out}")
    for name, (old, new) in BROKEN.items():
        assert old in GOOD_CPP, name
        code, out = run(GOOD_CPP.replace(old, new, 1))
        if code == 0:
            failures.append(f"not detected: {name}\n{out}")
    for failure in failures:
        print("check-jni-selftest:", failure)
    print(f"check-jni-selftest: {1 + len(BROKEN) - len(failures)} of {1 + len(BROKEN)} cases behaved")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
