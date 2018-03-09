/* Use ./TestAudio/audioExtract.py to create audioData.h - this converts a wave file to an array of doubles in C.
 * Copy audioData.h into same folder as this test then build and run - the program should give F0 of the signal in
 * audioData.h */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>

#include "warble.h"
#include "warble_complex.h"
#include "minunit.h"

#define SAMPLES 4410

#define MULT 1.0594630943591

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

MU_TEST(test1khz) {
	const double sampleRate = 44100;
	double powerRMS = 500; // 90 dBspl
	float signalFrequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	double audio[SAMPLES];

	for (int s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sampleRate);
		audio[s] = sin(2 * M_PI * signalFrequency * t) * (powerPeak);
	}

	double out[1] = {0};

	double freqs[1] = { 1000 };

	warble_generalized_goertzel(audio, SAMPLES, sampleRate, freqs, 1,out);

	double signal_rms = warble_compute_rms(audio, SAMPLES);

	mu_assert_double_eq(powerRMS, out[0], 0.1);

	mu_assert_double_eq(powerRMS, signal_rms, 0.1);
}

MU_TEST(testGenerateSignal) {
	double word_length = 0.0872; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int16_t triggers[2] = {9, 25};
	char payload[] = "parrot";

	warble_init(&cfg, sample_rate, 1720., MULT, 0, word_length, (int16_t)strlen(payload), triggers, 2);

	size_t windowSize = warble_generate_window_size(&cfg);
	double* signal = malloc(sizeof(double) * windowSize);
	memset(signal, 0, sizeof(double) * windowSize);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, payload, signal);

	// Check frequencies
	double rms[32];

	// Analyze first trigger
	warble_generalized_goertzel(signal, cfg.word_length, cfg.sampleRate, cfg.frequencies, WARBLE_PITCH_COUNT, rms);


	double signal_rms = warble_compute_rms(signal, cfg.word_length);

	mu_assert_double_eq(powerRMS, rms[9], 0.1);

	free(signal);
}

MU_TEST_SUITE(test_suite) {
	MU_RUN_TEST(test1khz);
	MU_RUN_TEST(testGenerateSignal);
}

int main(int argc, char** argv) {
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
	return minunit_status == 1 ? -1 : 0;
}
