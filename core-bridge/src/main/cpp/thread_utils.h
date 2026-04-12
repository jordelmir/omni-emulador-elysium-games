// ═══════════════════════════════════════════════════════════════
// Elysium Console — Thread Pinning Utilities
// ═══════════════════════════════════════════════════════════════
// Provides CPU affinity control for pinning emulation threads
// to high-performance cores (Cortex-X/A7x) on big.LITTLE SoCs.
// ═══════════════════════════════════════════════════════════════

#ifndef ELYSIUM_THREAD_UTILS_H
#define ELYSIUM_THREAD_UTILS_H

#include <sched.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#define THREAD_TAG "ElysiumThread"
#define THREAD_LOGI(...) __android_log_print(ANDROID_LOG_INFO, THREAD_TAG, __VA_ARGS__)
#define THREAD_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, THREAD_TAG, __VA_ARGS__)

namespace elysium {

/**
 * Reads the max frequency of a CPU core from sysfs.
 * Returns the frequency in KHz, or 0 on failure.
 */
inline unsigned long readCoreMaxFreq(int coreIndex) {
    char path[128];
    snprintf(path, sizeof(path),
             "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", coreIndex);

    FILE* f = fopen(path, "r");
    if (!f) {
        return 0;
    }

    unsigned long freq = 0;
    if (fscanf(f, "%lu", &freq) != 1) {
        freq = 0;
    }
    fclose(f);
    return freq;
}

/**
 * Detects the "prime" (highest performance) cores on the SoC by reading
 * max frequencies from sysfs. Cores with the highest max frequency are
 * considered prime cores.
 *
 * @param outCores  Array to store prime core indices
 * @param maxCores  Maximum number of entries in outCores
 * @return          Number of prime cores found
 */
inline int detectPrimeCores(int* outCores, int maxCores) {
    int numCpus = static_cast<int>(sysconf(_SC_NPROCESSORS_CONF));
    if (numCpus <= 0 || numCpus > 16) {
        numCpus = 8;
    }

    unsigned long maxFreq = 0;
    unsigned long freqs[16] = {0};

    // First pass: find the maximum frequency across all cores
    for (int i = 0; i < numCpus && i < 16; ++i) {
        freqs[i] = readCoreMaxFreq(i);
        if (freqs[i] > maxFreq) {
            maxFreq = freqs[i];
        }
    }

    if (maxFreq == 0) {
        // Fallback: assume cores 4-7 are big cores (common layout)
        int count = 0;
        for (int i = 4; i < numCpus && count < maxCores; ++i) {
            outCores[count++] = i;
        }
        THREAD_LOGI("Frequency detection failed, using fallback cores 4-%d", numCpus - 1);
        return count;
    }

    // Second pass: collect all cores matching the max frequency
    int count = 0;
    for (int i = 0; i < numCpus && i < 16 && count < maxCores; ++i) {
        if (freqs[i] == maxFreq) {
            outCores[count++] = i;
        }
    }

    THREAD_LOGI("Detected %d prime cores (max freq: %lu KHz)", count, maxFreq);
    return count;
}

/**
 * Pins the calling thread to the specified CPU cores using sched_setaffinity.
 *
 * @param coreIds   Array of core indices to pin to
 * @param numCores  Number of entries in coreIds
 * @return          true if successful, false otherwise
 */
inline bool pinThreadToCores(const int* coreIds, int numCores) {
    cpu_set_t mask;
    CPU_ZERO(&mask);

    for (int i = 0; i < numCores; ++i) {
        CPU_SET(coreIds[i], &mask);
        THREAD_LOGI("Adding core %d to affinity mask", coreIds[i]);
    }

    pid_t tid = gettid();
    if (sched_setaffinity(tid, sizeof(cpu_set_t), &mask) == -1) {
        THREAD_LOGE("sched_setaffinity failed for tid %d: %s (errno=%d)",
                     tid, strerror(errno), errno);
        return false;
    }

    THREAD_LOGI("Thread %d pinned to %d cores successfully", tid, numCores);
    return true;
}

/**
 * Auto-detects and pins the calling thread to the prime (highest-perf) cores.
 * @return true if pinning succeeded
 */
inline bool pinToPrimeCores() {
    int primeCores[8];
    int count = detectPrimeCores(primeCores, 8);
    if (count == 0) {
        THREAD_LOGE("No prime cores detected, cannot pin thread");
        return false;
    }
    return pinThreadToCores(primeCores, count);
}

} // namespace elysium

#endif // ELYSIUM_THREAD_UTILS_H
