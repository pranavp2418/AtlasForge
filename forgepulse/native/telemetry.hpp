#pragma once
#include <cstdint>
#include <deque>
#include <span>
#include <stdexcept>
#include <vector>

namespace forgepulse {
struct Sample {
    std::uint64_t sequence;
    std::int64_t event_ms;
    std::int32_t temperature_milli;
    bool operator==(const Sample&) const = default;
};
Sample decode(std::span<const std::uint8_t> frame);
enum class Ingest { accepted, replay };
struct Features { std::size_t count; double mean; double slope_per_minute; std::int32_t min, max; };

// A single device stream. The service adapter must provide tenant/device authorization.
class Window {
    std::int64_t duration_ms_;
    std::size_t capacity_;
    std::uint64_t last_sequence_ = 0;
    std::int64_t last_time_ = -1;
    std::deque<Sample> samples_, replay_history_;
public:
    explicit Window(std::int64_t duration_ms = 60000, std::size_t capacity = 256);
    Ingest ingest(Sample sample);
    Features features() const;
    std::uint64_t last_sequence() const { return last_sequence_; }
};
}
