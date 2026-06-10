/* Probe D: UINT16 r = 0xFFFF vs UINT16 r = 0xFFFFu
 * Validates that assignment-initializer suffix is benign for matching-width types. */
#include <gb/gb.h>

UINT16 r_a;
UINT16 r_b;

void main(void) {
    r_a = 0xFFFF;
    r_b = 0xFFFFu;
}
