;--------------------------------------------------------
; File Created by SDCC : free open source ISO C Compiler
; Version 4.5.1 #15267 (Mac OS X ppc)
;--------------------------------------------------------
	.module probe_c_unsigned_eq_zero
	
;--------------------------------------------------------
; Public variables in this module
;--------------------------------------------------------
	.globl _main
	.globl _test_u_suffix
	.globl _test_no_suffix
	.globl _result_b
	.globl _result_a
;--------------------------------------------------------
; special function registers
;--------------------------------------------------------
	.area _HRAM
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
	.area _DATA
_result_a::
	.ds 1
_result_b::
	.ds 1
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
	.area _INITIALIZED
;--------------------------------------------------------
; absolute external ram data
;--------------------------------------------------------
	.area _DABS (ABS)
;--------------------------------------------------------
; global & static initialisations
;--------------------------------------------------------
	.area _HOME
	.area _GSINIT
	.area _GSFINAL
	.area _GSINIT
;--------------------------------------------------------
; Home
;--------------------------------------------------------
	.area _HOME
	.area _HOME
;--------------------------------------------------------
; code
;--------------------------------------------------------
	.area _CODE
;probe_c_unsigned_eq_zero.c:8: void test_no_suffix(UINT8 x) {
;	---------------------------------
; Function test_no_suffix
; ---------------------------------
_test_no_suffix::
;probe_c_unsigned_eq_zero.c:9: if (x == 0) {
	or	a, a
	jr	NZ, 00102$
;probe_c_unsigned_eq_zero.c:10: result_a = 1u;
	ld	hl, #_result_a
	ld	(hl), #0x01
	ret
00102$:
;probe_c_unsigned_eq_zero.c:12: result_a = 0u;
	xor	a, a
	ld	(#_result_a),a
;probe_c_unsigned_eq_zero.c:14: }
	ret
;probe_c_unsigned_eq_zero.c:16: void test_u_suffix(UINT8 x) {
;	---------------------------------
; Function test_u_suffix
; ---------------------------------
_test_u_suffix::
;probe_c_unsigned_eq_zero.c:17: if (x == 0u) {
	or	a, a
	jr	NZ, 00102$
;probe_c_unsigned_eq_zero.c:18: result_b = 1u;
	ld	hl, #_result_b
	ld	(hl), #0x01
	ret
00102$:
;probe_c_unsigned_eq_zero.c:20: result_b = 0u;
	xor	a, a
	ld	(#_result_b),a
;probe_c_unsigned_eq_zero.c:22: }
	ret
;probe_c_unsigned_eq_zero.c:24: void main(void) {
;	---------------------------------
; Function main
; ---------------------------------
_main::
;probe_c_unsigned_eq_zero.c:25: test_no_suffix(0);
	xor	a, a
	call	_test_no_suffix
;probe_c_unsigned_eq_zero.c:26: test_u_suffix(0);
	xor	a, a
;probe_c_unsigned_eq_zero.c:27: }
	jp	_test_u_suffix
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
