#include <cassert>
#include <cstdint>
#include <cstdio>
#include <vector>
#include "xenia/gpu/vblank_pacer.h"

using xe::gpu::VblankPacer;

// K11: the guest vblank schedule driven by a fake clock, the way the vblank thread
// drives it: advance, raise when told to, sleep until the next deadline plus however
// late the wakeup is.
constexpr uint64_t kNs = 1000000000;  // the Android guest clock: 1 tick = 1 ns
constexpr uint64_t kMs = 1000000;

struct Run {
  std::vector<uint64_t> deadlines;  // of every vblank raised
  uint64_t rebases = 0;
  uint64_t end = 0;
};

// Runs from `start` through `until`, waking `late(i)` ticks after each requested deadline.
template <typename Late>
static Run Drive(VblankPacer& pacer, uint64_t start, uint64_t until, uint64_t frequency, uint32_t hz, Late late) {
  Run run;
  uint64_t now = start;
  for (int i = 0; now <= until; ++i) {
    const VblankPacer::Step step = pacer.Advance(now, frequency, hz);
    if (step.fire) run.deadlines.push_back(step.deadline);
    if (step.rebased) ++run.rebases;
    now = (step.next_deadline > now ? step.next_deadline : now) + late(i);
  }
  run.end = now;
  return run;
}

static void SixtyHertzIsSixtyVblanksPerGuestSecondExactly() {
  VblankPacer pacer;
  const uint64_t t0 = 5 * kNs;
  pacer.Start(t0);
  const Run run = Drive(pacer, t0, t0 + 10 * kNs, kNs, 60, [](int) { return uint64_t(0); });
  // 1e9 / 60 is not whole: the remainder is carried, so no drift builds up.
  assert(run.deadlines.size() == 600);
  for (size_t n = 0; n < run.deadlines.size(); ++n) {
    assert(run.deadlines[n] == t0 + (n + 1) * kNs / 60);
  }
  assert(run.deadlines.back() == t0 + 10 * kNs);
  assert(run.rebases == 0);
}

static void LateWakeupsNeverShiftTheCadence() {
  VblankPacer pacer;
  pacer.Start(0);
  // Every wakeup oversleeps by 2 to 9 ms (a loaded phone): still the same deadlines.
  const Run run = Drive(pacer, 0, 10 * kNs, kNs, 60, [](int i) { return uint64_t(2 + i % 8) * kMs; });
  assert(run.rebases == 0);
  assert(run.deadlines.size() >= 599);
  for (size_t n = 0; n < run.deadlines.size(); ++n) {
    assert(run.deadlines[n] == (n + 1) * kNs / 60);
  }
}

static void ALateWakeupWithinAnIntervalKeepsThePhase() {
  VblankPacer pacer;
  pacer.Start(0);
  const uint64_t period = kNs / 50;  // 20 ms
  VblankPacer::Step step = pacer.Advance(period + 18 * kMs, kNs, 50);
  assert(step.fire && !step.rebased);
  assert(step.deadline == period);
  assert(step.next_deadline == 2 * period);  // 2 ms away: back on the grid
  step = pacer.Advance(period + 18 * kMs, kNs, 50);
  assert(!step.fire);
}

static void AStallRaisesOneVblankNotABurst() {
  VblankPacer pacer;
  pacer.Start(0);
  const uint64_t period = kNs / 50;
  // 10.5 intervals late (a GC pause, a debugger): one vblank, the schedule restarts.
  const uint64_t now = period + 10 * period + period / 2;
  VblankPacer::Step step = pacer.Advance(now, kNs, 50);
  assert(step.fire && step.rebased);
  assert(step.dropped == 10);
  assert(step.deadline == now);
  assert(step.next_deadline == now + period);
  step = pacer.Advance(now + 1, kNs, 50);
  assert(!step.fire);
  assert(pacer.rebases() == 1 && pacer.dropped() == 10 && pacer.fired() == 1);
  // The title opening long after the thread started is the same case.
  VblankPacer idle;
  idle.Start(0);
  step = idle.Advance(30 * kNs, kNs, 60);
  assert(step.fire && step.rebased);
}

static void ExactlyOneIntervalLateCatchesUpOnce() {
  VblankPacer pacer;
  pacer.Start(0);
  const uint64_t period = kNs / 50;
  const uint64_t now = 2 * period;  // the first deadline was `period`
  VblankPacer::Step step = pacer.Advance(now, kNs, 50);
  assert(step.fire && !step.rebased && step.deadline == period);
  step = pacer.Advance(now, kNs, 50);  // the missed one, once
  assert(step.fire && !step.rebased && step.deadline == 2 * period);
  step = pacer.Advance(now, kNs, 50);
  assert(!step.fire && step.next_deadline == 3 * period);
}

static void ANewRateRunsFromTheLastDeadline() {
  VblankPacer pacer;
  pacer.Start(0);
  VblankPacer::Step step = pacer.Advance(kNs / 60, kNs, 60);
  assert(step.fire && step.deadline == kNs / 60);
  step = pacer.Advance(kNs / 60, kNs, 50);  // PAL-50 chosen
  assert(!step.fire && step.next_deadline == kNs / 60 + kNs / 50);
  // The console's own timebase: 49.875 MHz / 50 Hz is whole.
  VblankPacer xbox;
  xbox.Start(0);
  const Run run = Drive(xbox, 0, 49875000, 49875000, 50, [](int) { return uint64_t(0); });
  assert(run.deadlines.size() == 50 && run.deadlines[0] == 997500 && run.deadlines.back() == 49875000);
}

static void NoRateOrABackwardClockNeverStallsTheThread() {
  VblankPacer pacer;
  pacer.Start(100);
  VblankPacer::Step step = pacer.Advance(200, kNs, 0);
  assert(!step.fire && step.next_deadline == 200);  // nothing due, nothing divided by zero
  step = pacer.Advance(200, 30, 60);
  assert(!step.fire && step.next_deadline == 200);
  // The clock went back: start over from now rather than sleep until the old deadline.
  pacer.Start(10 * kNs);
  step = pacer.Advance(1 * kNs, kNs, 60);
  assert(step.fire && step.rebased && step.dropped == 0);
  assert(step.next_deadline == kNs + kNs / 60);
}

int main() {
  SixtyHertzIsSixtyVblanksPerGuestSecondExactly();
  LateWakeupsNeverShiftTheCadence();
  ALateWakeupWithinAnIntervalKeepsThePhase();
  AStallRaisesOneVblankNotABurst();
  ExactlyOneIntervalLateCatchesUpOnce();
  ANewRateRunsFromTheLastDeadline();
  NoRateOrABackwardClockNeverStallsTheThread();
  std::puts("vblank pacer: passed");
}
