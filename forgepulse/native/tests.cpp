#include "telemetry.hpp"
#include "crc32.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <random>
#include <string>
static int checks=0;
static void check(bool value) { ++checks; if(!value) throw std::runtime_error("Assertion failed"); }
template<class F> static void rejects(F operation) { ++checks;try {operation();}catch(const std::invalid_argument&){return;}throw std::runtime_error("Expected rejection"); }
int main() {
    const std::string known="123456789";check(forge_crc32(reinterpret_cast<const std::uint8_t*>(known.data()),known.size())==0xcbf43926U);
    rejects([]{forgepulse::decode({});});rejects([]{forgepulse::Window(0);});
    forgepulse::Window window(60000,64);check(window.features().count==0);
    check(window.ingest({1,1000,20000})==forgepulse::Ingest::accepted);check(window.ingest({1,1000,20000})==forgepulse::Ingest::replay);
    rejects([&]{window.ingest({1,1000,20001});});rejects([&]{window.ingest({3,2000,20000});});
    rejects([&]{window.ingest({2,999,20000});});check(window.last_sequence()==1);
    check(window.ingest({2,61000,30000})==forgepulse::Ingest::accepted);check(std::abs(window.features().slope_per_minute-10)<1e-9);
    std::mt19937 random(413);std::vector<forgepulse::Sample> oracle;
    for(std::uint64_t i=3;i<=1002;i++) {
        forgepulse::Sample sample{i,61000+std::int64_t(i-2)*1000,20000+std::int32_t(random()%10000)};
        window.ingest(sample);oracle.push_back(sample);
        while(oracle.size()>64 || sample.event_ms-oracle.front().event_ms>60000)oracle.erase(oracle.begin());
        // The initial seed remains only until its retention window expires.
        if(i<64) continue;
        std::int64_t sum=0;std::int32_t low=200000,high=-273150;
        for(const auto& item:oracle){sum+=item.temperature_milli;low=std::min(low,item.temperature_milli);high=std::max(high,item.temperature_milli);}
        auto features=window.features();check(features.count==oracle.size());check(std::abs(features.mean-double(sum)/oracle.size()/1000)<1e-9);
        check(features.min==low);check(features.max==high);
    }
    rejects([&]{window.ingest({1,1000,20000});});
    std::cout << "ForgePulse: " << checks << " assertions passed; rolling-window oracle and replay fencing.\n";
}
