;--------------------------------------------------------
; File Created by SDCC : free open source ISO C Compiler
; Version 4.5.1 #15267 (Mac OS X ppc)
;--------------------------------------------------------
	.module probe_e_explicit_cast
	
;--------------------------------------------------------
; Public variables in this module
;--------------------------------------------------------
	.globl _main
	.globl _test_explicit_cast_u
	.globl _test_explicit_cast
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
;probe_e_explicit_cast.c:8: void test_explicit_cast(INT16 a) {
;	---------------------------------
; Function test_explicit_cast
; ---------------------------------
_test_explicit_cast::
	ld	b, d
;probe_e_explicit_cast.c:9: if (a < (INT16)0) {
	bit	7, b
	jr	Z, 00102$
;probe_e_explicit_cast.c:10: result_a = 1u;
	ld	hl, #_result_a
	ld	(hl), #0x01
	ret
00102$:
;probe_e_explicit_cast.c:12: result_a = 0u;
	xor	a, a
	ld	(#_result_a),a
;probe_e_explicit_cast.c:14: }
	ret
;probe_e_explicit_cast.c:16: void test_explicit_cast_u(INT16 a) {
;	---------------------------------
; Function test_explicit_cast_u
; ---------------------------------
_test_explicit_cast_u::
	ld	b, d
;probe_e_explicit_cast.c:20: if (a < (INT16)0u) {
	bit	7, b
	jr	Z, 00102$
;probe_e_explicit_cast.c:21: result_b = 1u;
	ld	hl, #_result_b
	ld	(hl), #0x01
	ret
00102$:
;probe_e_explicit_cast.c:23: result_b = 0u;
	xor	a, a
	ld	(#_result_b),a
;probe_e_explicit_cast.c:25: }
	ret
;probe_e_explicit_cast.c:27: void main(void) {
;	---------------------------------
; Function main
; ---------------------------------
_main::
;probe_e_explicit_cast.c:28: test_explicit_cast(-63);
	ld	de, #0xffc1
	call	_test_explicit_cast
;probe_e_explicit_cast.c:29: test_explicit_cast_u(-63);
	ld	de, #0xffc1
;probe_e_explicit_cast.c:30: }
	jp	_test_explicit_cast_u
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
