/* Probe A: signed < 0 vs signed < 0u
 * Compares two functions that should be IDENTICAL if the suffix is irrelevant. */
#include <gb/gb.h>

UINT8 result_a;
UINT8 result_b;

void test_no_suffix(INT16 a) {
    if (a < 0) {
        result_a = 1u;
    } else {
        result_a = 0u;
    }
}

void test_u_suffix(INT16 a) {
    if (a < 0u) {
        result_b = 1u;
    } else {
        result_b = 0u;
    }
}

void main(void) {
    test_no_suffix(-63);
    test_u_suffix(-63);
}
