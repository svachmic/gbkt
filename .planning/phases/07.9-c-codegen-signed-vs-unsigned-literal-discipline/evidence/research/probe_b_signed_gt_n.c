/* Probe B: INT16 a > 8 vs INT16 a > 8u
 * Mirrors the racer camera clamp `rawY > 8u` for rawY=-63 (after _car_y=5).
 * Expected behaviour: a=-63, should be FALSE (a is negative, definitely not >8).
 * Bug behaviour: with `8u` suffix, signed→unsigned promotion makes -63 ≈ 4.29e9, 
 * comparison returns TRUE. */
#include <gb/gb.h>

UINT8 result_a;
UINT8 result_b;

void test_no_suffix(INT16 a) {
    if (a > 8) {
        result_a = 1u;
    } else {
        result_a = 0u;
    }
}

void test_u_suffix(INT16 a) {
    if (a > 8u) {
        result_b = 1u;
    } else {
        result_b = 0u;
    }
}

void main(void) {
    test_no_suffix(-63);
    test_u_suffix(-63);
}
