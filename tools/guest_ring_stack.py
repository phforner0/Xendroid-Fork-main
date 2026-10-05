"""Replays the pops and pushes of guest lock-free stacks recorded by
log_guest_calls_ring (the ring the emulator logs when the guest crashes) and
reports a node popped twice without a push in between - a double allocation,
the mark of an ABA in the stack - or pushed while already in, with the
operations on that stack in between.

The pop function takes the head pointer in r3 and returns the node in r3; the
push function takes the head pointer in r3 and the node in r4. Both must be in
log_guest_calls_at, with "r3+0:1" or more in log_guest_call_fields (the head).
The defaults are Need for Speed Most Wanted's pool pop and push.

Usage: guest_ring_stack.py [--pop ADDR] [--push ADDR] <xe.log>
"""
import collections
import re
import sys

args = sys.argv[1:]
pop_fn, push_fn = '898D7558', '898D7538'
while args and args[0].startswith('--'):
    if args[0] == '--pop':
        pop_fn = args[1].upper()
    elif args[0] == '--push':
        push_fn = args[1].upper()
    else:
        sys.exit(__doc__)
    args = args[2:]
if len(args) != 1:
    sys.exit(__doc__)

call_re = re.compile(
    r'ring (\d+) call (%s|%s) thread ([0-9A-F]{4}) lr=([0-9A-F]{8}) '
    r'r3=([0-9A-F]{8}) r4=([0-9A-F]{8}) r5=[0-9A-F]{8} r11=[0-9A-F]{8} \|(.*)$'
    % (pop_fn, push_fn))
ret_re = re.compile(r'ring (\d+) ret (%s) thread ([0-9A-F]{4}) r3=([0-9A-F]{8})'
                    % pop_fn)
pending = {}  # thread -> (stack, call index, head word)
state = {}  # node -> ('in' | 'out', stack, index, thread)
history = collections.defaultdict(list)  # stack -> [(index, text)]
reported = 0
for raw in open(args[0], 'rb'):
    if b' ring ' not in raw:
        continue
    line = raw.decode('latin-1')
    m = call_re.search(line)
    if m:
        index, fn, thread, lr, r3, r4, words = m.groups()
        head = words.split()[0] if words.split() else '?'
        if fn == pop_fn:
            pending[thread] = (r3, int(index), head)
            continue
        history[r3].append((int(index), f'{index} T{thread} push {r4} (head was {head})'))
        previous = state.get(r4)
        if previous and previous[0] == 'in' and previous[1] == r3 and reported < 10:
            reported += 1
            print(f'PUSHED TWICE {r4} onto {r3} at {index} T{thread}; '
                  f'in since {previous[2]} T{previous[3]}')
        state[r4] = ('in', r3, int(index), thread)
        continue
    m = ret_re.search(line)
    if not m:
        continue
    index, fn, thread, node = m.groups()
    call = pending.pop(thread, None)
    if not call:
        continue
    stack, call_index, head = call
    history[stack].append(
        (int(index), f'{call_index}->{index} T{thread} pop -> {node} (head was {head})'))
    if node == '00000000':
        continue
    previous = state.get(node)
    if previous and previous[0] == 'out' and previous[1] == stack and reported < 10:
        reported += 1
        print(f'DOUBLE POP {node} from {stack} at {call_index}->{index} '
              f'T{thread}; already popped at {previous[2]} T{previous[3]}')
        for i, text in history[stack]:
            if previous[2] - 40 <= i <= int(index):
                print('    ', text)
    state[node] = ('out', stack, int(index), thread)
print('operations per stack:', {k: len(v) for k, v in history.items()})
