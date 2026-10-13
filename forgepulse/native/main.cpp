#include "telemetry.hpp"
#include <array>
#include <fstream>
#include <iostream>
#include <iterator>
#include <string>
#include <vector>
int main(int argc, char** argv) {
    try {
        if (argc != 2) throw std::invalid_argument("Usage: forgepulse-replay frames.bin");
        std::ifstream input(argv[1],std::ios::binary); if (!input) throw std::invalid_argument("Cannot open input");
        forgepulse::Window window; std::array<std::uint8_t,28> frame{}; std::size_t count=0;
        while (input.read(reinterpret_cast<char*>(frame.data()),frame.size()) || input.gcount()) {
            if (input.gcount()!=28 || ++count>1000000) throw std::invalid_argument("Incomplete or oversized frame stream");
            window.ingest(forgepulse::decode(frame));
        }
        if (!count || !input.eof()) throw std::invalid_argument("Empty or unreadable frame stream");
        const auto result=window.features();
        std::cout << "{\"last_sequence\":" << window.last_sequence() << ",\"window_count\":" << result.count
                  << ",\"mean_celsius\":" << result.mean << ",\"slope_per_minute\":" << result.slope_per_minute << "}\n";
    } catch(const std::exception& error) { std::cerr << error.what() << '\n'; return 2; }
}
