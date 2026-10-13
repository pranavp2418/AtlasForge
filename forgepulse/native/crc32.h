#ifndef FORGEPULSE_CRC32_H
#define FORGEPULSE_CRC32_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
uint32_t forge_crc32(const uint8_t *bytes, size_t length);
#ifdef __cplusplus
}
#endif
#endif
