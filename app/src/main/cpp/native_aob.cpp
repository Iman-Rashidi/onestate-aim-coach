#include "native_aob.h"

#include <jni.h>

#include <limits>
#include <vector>

bool matchMask(
        const std::uint8_t* pattern,
        const std::uint8_t* mask,
        const std::uint8_t* data,
        std::size_t patternLength,
        std::size_t dataLength,
        std::size_t offset) {
    if (pattern == nullptr || mask == nullptr || data == nullptr) return false;
    if (offset > dataLength || patternLength > dataLength - offset) return false;

    for (std::size_t i = 0; i < patternLength; ++i) {
        if (mask[i] != 0U && pattern[i] != data[offset + i]) return false;
    }
    return true;
}

std::vector<std::int32_t> findAllMatches(
        const std::uint8_t* pattern,
        const std::uint8_t* mask,
        const std::uint8_t* data,
        std::size_t patternLength,
        std::size_t dataLength) {
    std::vector<std::int32_t> matches;
    if (pattern == nullptr || mask == nullptr || data == nullptr
            || patternLength == 0U || patternLength > dataLength) {
        return matches;
    }

    const std::size_t lastOffset = dataLength - patternLength;
    for (std::size_t offset = 0; offset <= lastOffset; ++offset) {
        if (matchMask(pattern, mask, data, patternLength, dataLength, offset)) {
            if (offset > static_cast<std::size_t>(std::numeric_limits<std::int32_t>::max())) {
                break;
            }
            matches.push_back(static_cast<std::int32_t>(offset));
        }
    }
    return matches;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iman_onestateaimcoach_NativeBridge_nativeMatchMask(
        JNIEnv* env, jclass, jbyteArray patternArray, jbyteArray maskArray,
        jbyteArray dataArray, jint offset) {
    if (patternArray == nullptr || maskArray == nullptr || dataArray == nullptr || offset < 0) {
        return JNI_FALSE;
    }

    const jsize patternLength = env->GetArrayLength(patternArray);
    const jsize maskLength = env->GetArrayLength(maskArray);
    const jsize dataLength = env->GetArrayLength(dataArray);
    if (patternLength != maskLength || static_cast<jsize>(offset) > dataLength
            || patternLength > dataLength - static_cast<jsize>(offset)) {
        return JNI_FALSE;
    }

    jbyte* pattern = env->GetByteArrayElements(patternArray, nullptr);
    if (pattern == nullptr) return JNI_FALSE;
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    if (mask == nullptr) {
        env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
        return JNI_FALSE;
    }
    jbyte* data = env->GetByteArrayElements(dataArray, nullptr);
    if (data == nullptr) {
        env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
        return JNI_FALSE;
    }

    const bool matched = matchMask(
            reinterpret_cast<const std::uint8_t*>(pattern),
            reinterpret_cast<const std::uint8_t*>(mask),
            reinterpret_cast<const std::uint8_t*>(data),
            static_cast<std::size_t>(patternLength),
            static_cast<std::size_t>(dataLength),
            static_cast<std::size_t>(offset));

    env->ReleaseByteArrayElements(dataArray, data, JNI_ABORT);
    env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
    return matched ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_iman_onestateaimcoach_NativeBridge_nativeFindAll(
        JNIEnv* env, jclass, jbyteArray patternArray, jbyteArray maskArray,
        jbyteArray dataArray) {
    if (patternArray == nullptr || maskArray == nullptr || dataArray == nullptr) {
        return env->NewIntArray(0);
    }

    const jsize patternLength = env->GetArrayLength(patternArray);
    const jsize maskLength = env->GetArrayLength(maskArray);
    const jsize dataLength = env->GetArrayLength(dataArray);
    if (patternLength == 0 || patternLength != maskLength || patternLength > dataLength) {
        return env->NewIntArray(0);
    }

    jbyte* pattern = env->GetByteArrayElements(patternArray, nullptr);
    if (pattern == nullptr) return nullptr;
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    if (mask == nullptr) {
        env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
        return nullptr;
    }
    jbyte* data = env->GetByteArrayElements(dataArray, nullptr);
    if (data == nullptr) {
        env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
        return nullptr;
    }

    const std::vector<std::int32_t> matches = findAllMatches(
            reinterpret_cast<const std::uint8_t*>(pattern),
            reinterpret_cast<const std::uint8_t*>(mask),
            reinterpret_cast<const std::uint8_t*>(data),
            static_cast<std::size_t>(patternLength),
            static_cast<std::size_t>(dataLength));

    jintArray result = env->NewIntArray(static_cast<jsize>(matches.size()));
    if (result != nullptr && !matches.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(matches.size()),
                reinterpret_cast<const jint*>(matches.data()));
    }

    env->ReleaseByteArrayElements(dataArray, data, JNI_ABORT);
    env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    env->ReleaseByteArrayElements(patternArray, pattern, JNI_ABORT);
    return result;
}
