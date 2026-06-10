/* Probe E: Validate Option E: explicit (INT16)0 cast at compare site
 * The visitor wraps the literal: `(INT16)x < (INT16)0` */
#include <gb/gb.h>

UINT8 result_a;
UINT8 result_b;

void test_explicit_cast(INT16 a) {
    if (a < (INT16)0) {
        result_a = 1u;
    } else {
        result_a = 0u;
    }
}

void test_explicit_cast_u(INT16 a) {
    /* Even with (INT16) wrap, the literal still carries its own type.
     * `(INT16)0` should produce signed 0. Compare against `(INT16)0u` — does 
     * the (INT16) outer cast normalise the right side? */
    if (a < (INT16)0u) {
        result_b = 1u;
    } else {
        result_b = 0u;
    }
}

void main(void) {
    test_explicit_cast(-63);
    test_explicit_cast_u(-63);
}
