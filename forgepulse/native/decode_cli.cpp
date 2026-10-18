#include "telemetry.hpp"
#include <array>
#include <iostream>
#include <iterator>
int main() {
    std::array<std::uint8_t,29> bytes{};
    std::cin.read(reinterpret_cast<char*>(bytes.data()),bytes.size());
    if(std::cin.gcount()!=28){std::cerr<<"Expected exactly 28 bytes\n";return 2;}
    try {
        auto sample=forgepulse::decode(std::span<const std::uint8_t>(bytes.data(),28));
        std::cout<<sample.sequence<<" "<<sample.event_ms<<" "<<sample.temperature_milli<<"\n";
    } catch(const std::exception&) { std::cerr<<"Invalid frame\n";return 2; }
}
