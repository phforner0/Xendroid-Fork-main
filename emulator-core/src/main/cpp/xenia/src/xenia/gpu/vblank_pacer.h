#ifndef XENIA_GPU_VBLANK_PACER_H_
#define XENIA_GPU_VBLANK_PACER_H_

#include <cstdint>

namespace xe::gpu {

// K11: the guest vblank clock as absolute deadlines on the guest tick clock, apart
// from every presenter and frame-generation deadline. A vblank is due one interval
// after the previous deadline, not after the moment the thread woke, so a late
// wakeup never shifts the cadence; the interval carries the remainder of
// frequency / hz, so 60 Hz is exactly 60 vblanks per guest second. A thread more
// than one interval late (a stall, a debugger, the title not open yet, the clock
// going back) restarts the schedule from now and raises one vblank instead of a
// burst of the missed ones. Pure: the vblank thread feeds it the clock, tests feed
// it a fake one.
class VblankPacer {
 public:
  struct Step {
    bool fire = false;           // raise a vblank now
    bool rebased = false;        // the schedule restarted from now
    uint64_t dropped = 0;        // missed vblanks the restart did not raise
    uint64_t deadline = 0;       // the deadline this vblank stands for (now, after a restart)
    uint64_t next_deadline = 0;  // sleep until this guest tick
  };

  // The schedule starts at `now`: the first vblank is one interval later.
  void Start(uint64_t now) {
    last_ = now;
    carry_ = 0;
    started_ = true;
  }

  // Advances to `now` (guest ticks, `frequency` per second) at `hz` vblanks per second.
  Step Advance(uint64_t now, uint64_t frequency, uint32_t hz) {
    Step step;
    if (!started_) Start(now);
    if (!hz || frequency < hz) {  // no rate to keep: nothing is due
      step.next_deadline = now;
      return step;
    }
    if (frequency != frequency_ || hz != hz_) {
      // A new rate (50/60 Hz, another clock) runs from the last deadline on.
      frequency_ = frequency;
      hz_ = hz;
      period_ = frequency / hz;
      remainder_ = frequency % hz;
      carry_ = 0;
    }
    const uint64_t deadline = last_ + Interval();
    if (now < last_ || (now >= deadline && now - deadline > period_)) {
      step.rebased = true;
      step.dropped = now < last_ ? 0 : (now - deadline) / period_;
      ++rebases_;
      dropped_ += step.dropped;
      last_ = now;
      carry_ = 0;
    } else if (now >= deadline) {
      carry_ += remainder_;
      if (carry_ >= hz_) carry_ -= hz_;
      last_ = deadline;
    } else {
      step.next_deadline = deadline;
      return step;
    }
    step.fire = true;
    step.deadline = last_;
    ++fired_;
    step.next_deadline = last_ + Interval();
    return step;
  }

  uint64_t fired() const { return fired_; }
  uint64_t rebases() const { return rebases_; }
  uint64_t dropped() const { return dropped_; }

 private:
  // period_ or period_ + 1: over hz intervals they add up to exactly frequency.
  uint64_t Interval() const { return period_ + (carry_ + remainder_ >= hz_ ? 1 : 0); }

  bool started_ = false;
  uint64_t last_ = 0;
  uint64_t frequency_ = 0;
  uint32_t hz_ = 0;
  uint64_t period_ = 0;
  uint64_t remainder_ = 0;
  uint64_t carry_ = 0;
  uint64_t fired_ = 0;
  uint64_t rebases_ = 0;
  uint64_t dropped_ = 0;
};

}  // namespace xe::gpu

#endif  // XENIA_GPU_VBLANK_PACER_H_
