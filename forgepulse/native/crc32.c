#include "crc32.h"
uint32_t forge_crc32(const uint8_t *bytes, size_t length) {
    uint32_t crc = UINT32_C(0xffffffff);
    for (size_t i = 0; i < length; ++i) {
        crc ^= bytes[i];
        for (unsigned bit = 0; bit < 8; ++bit)
            crc = (crc >> 1) ^ (UINT32_C(0xedb88320) & (UINT32_C(0) - (crc & UINT32_C(1))));
    }
    return ~crc;
}
