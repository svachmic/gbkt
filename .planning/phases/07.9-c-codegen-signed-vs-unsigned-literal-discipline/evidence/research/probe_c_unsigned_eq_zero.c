/* Probe C: UINT8 x == 0 vs UINT8 x == 0u
 * For unsigned-typed targets the suffix should be a no-op. */
#include <gb/gb.h>

UINT8 result_a;
UINT8 result_b;

void test_no_suffix(UINT8 x) {
    if (x == 0) {
        result_a = 1u;
    } else {
        result_a = 0u;
    }
}

void test_u_suffix(UINT8 x) {
    if (x == 0u) {
        result_b = 1u;
    } else {
        result_b = 0u;
    }
}

void main(void) {
    test_no_suffix(0);
    test_u_suffix(0);
}
