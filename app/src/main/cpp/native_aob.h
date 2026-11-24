#ifndef ONESTATE_NATIVE_AOB_H
#define ONESTATE_NATIVE_AOB_H

#include <cstddef>
#include <cstdint>
#include <vector>

// Both lengths are required for strict bounds checking on raw buffers.
bool matchMask(
        const std::uint8_t* pattern,
        const std::uint8_t* mask,
        const std::uint8_t* data,
        std::size_t patternLength,
        std::size_t dataLength,
        std::size_t offset);

std::vector<std::int32_t> findAllMatches(
        const std::uint8_t* pattern,
        const std::uint8_t* mask,
        const std::uint8_t* data,
        std::size_t patternLength,
        std::size_t dataLength);

#endif
