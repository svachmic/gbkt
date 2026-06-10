/* Probe F: Three-way comparison of right-side literal forms */
#include <gb/gb.h>

UINT8 ra; UINT8 rb; UINT8 rc;

void test_bare(INT16 a) { if (a < 0) ra = 1u; else ra = 0u; }
void test_suffix(INT16 a) { if (a < 0u) rb = 1u; else rb = 0u; }
void test_cast(INT16 a) { if (a < (INT16)0) rc = 1u; else rc = 0u; }

void main(void) {
    test_bare(-1);
    test_suffix(-1);
    test_cast(-1);
}
