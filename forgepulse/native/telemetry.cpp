#include "telemetry.hpp"
#include "crc32.h"
#include <algorithm>
#include <bit>
#include <cmath>
#include <limits>

namespace forgepulse {
static std::uint64_t little(std::span<const std::uint8_t> data, std::size_t offset, std::size_t width) {
    std::uint64_t value = 0;
    for (std::size_t i = 0; i < width; ++i) value |= std::uint64_t(data[offset+i]) << (i*8);
    return value;
}
static void validate(Sample sample) {
    if (!sample.sequence || sample.sequence == std::numeric_limits<std::uint64_t>::max() || sample.event_ms < 0 ||
        sample.temperature_milli < -273150 || sample.temperature_milli > 200000)
        throw std::invalid_argument("Invalid telemetry field");
}
Sample decode(std::span<const std::uint8_t> frame) {
    if (frame.size() != 28 || frame[0] != 'F' || frame[1] != 'G' || frame[2] != 'P' || frame[3] != '1')
        throw std::invalid_argument("Invalid frame length or magic");
    if (forge_crc32(frame.data(), 24) != little(frame,24,4)) throw std::invalid_argument("Frame checksum mismatch");
    auto timestamp = little(frame,12,8);
    if (timestamp > std::uint64_t(std::numeric_limits<std::int64_t>::max())) throw std::invalid_argument("Invalid timestamp");
    Sample result{little(frame,4,8),static_cast<std::int64_t>(timestamp),
        std::bit_cast<std::int32_t>(static_cast<std::uint32_t>(little(frame,20,4)))};
    validate(result); return result;
}
Window::Window(std::int64_t duration_ms, std::size_t capacity): duration_ms_(duration_ms),capacity_(capacity) {
    if (duration_ms <= 0 || !capacity || capacity > 4096) throw std::invalid_argument("Invalid window bounds");
}
Ingest Window::ingest(Sample sample) {
    validate(sample);
    if (sample.sequence <= last_sequence_) {
        auto match = std::find_if(replay_history_.begin(), replay_history_.end(), [&](const auto& old){return old.sequence == sample.sequence;});
        if (match == replay_history_.end()) throw std::invalid_argument("Replay outside retained history");
        if (*match != sample) throw std::invalid_argument("Conflicting sequence replay");
        return Ingest::replay;
    }
    if (sample.sequence != last_sequence_ + 1) throw std::invalid_argument("Sequence gap; replay missing frame first");
    if (sample.event_ms < last_time_) throw std::invalid_argument("Late event; correction workflow required");
    // All semantic validation precedes state mutation. Memory allocation failure is outside this kernel's contract.
    samples_.push_back(sample); replay_history_.push_back(sample);
    while (samples_.size() > capacity_ || sample.event_ms-samples_.front().event_ms > duration_ms_) samples_.pop_front();
    while (replay_history_.size() > 256) replay_history_.pop_front();
    last_sequence_ = sample.sequence; last_time_ = sample.event_ms; return Ingest::accepted;
}
Features Window::features() const {
    if (samples_.empty()) return {0,0,0,0,0};
    std::int64_t sum = 0; auto low = samples_.front().temperature_milli, high = low;
    double xmean = 0, ymean = 0, cross = 0, variance = 0; std::size_t count = 0;
    const auto origin = samples_.front().event_ms;
    for (const auto& sample: samples_) {
        sum += sample.temperature_milli; low = std::min(low,sample.temperature_milli); high = std::max(high,sample.temperature_milli);
        const double x = double(sample.event_ms-origin)/60000.0, y = sample.temperature_milli/1000.0;
        ++count; const double dx=x-xmean,dy=y-ymean; xmean+=dx/count; ymean+=dy/count;
        variance+=dx*(x-xmean); cross+=dx*(y-ymean);
    }
    return {samples_.size(),double(sum)/samples_.size()/1000.0,variance>1e-12?cross/variance:0,low,high};
}
}
