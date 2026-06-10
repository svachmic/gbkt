;--------------------------------------------------------
; File Created by SDCC : free open source ISO C Compiler
; Version 4.5.1 #15267 (Mac OS X ppc)
;--------------------------------------------------------
	.module probe_a_signed_lt_zero
	
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
;probe_a_signed_lt_zero.c:8: void test_no_suffix(INT16 a) {
;	---------------------------------
; Function test_no_suffix
; ---------------------------------
_test_no_suffix::
	ld	b, d
;probe_a_signed_lt_zero.c:9: if (a < 0) {
	bit	7, b
	jr	Z, 00102$
;probe_a_signed_lt_zero.c:10: result_a = 1u;
	ld	hl, #_result_a
	ld	(hl), #0x01
	ret
00102$:
;probe_a_signed_lt_zero.c:12: result_a = 0u;
	xor	a, a
	ld	(#_result_a),a
;probe_a_signed_lt_zero.c:14: }
	ret
;probe_a_signed_lt_zero.c:16: void test_u_suffix(INT16 a) {
;	---------------------------------
; Function test_u_suffix
; ---------------------------------
_test_u_suffix::
;probe_a_signed_lt_zero.c:20: result_b = 0u;
	xor	a, a
	ld	(#_result_b),a
;probe_a_signed_lt_zero.c:22: }
	ret
;probe_a_signed_lt_zero.c:24: void main(void) {
;	---------------------------------
; Function main
; ---------------------------------
_main::
;probe_a_signed_lt_zero.c:25: test_no_suffix(-63);
	ld	de, #0xffc1
	call	_test_no_suffix
;probe_a_signed_lt_zero.c:26: test_u_suffix(-63);
	ld	de, #0xffc1
;probe_a_signed_lt_zero.c:27: }
	jp	_test_u_suffix
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
