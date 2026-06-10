;--------------------------------------------------------
; File Created by SDCC : free open source ISO C Compiler
; Version 4.5.1 #15267 (Mac OS X ppc)
;--------------------------------------------------------
	.module probe_b_signed_gt_n
	
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
;probe_b_signed_gt_n.c:11: void test_no_suffix(INT16 a) {
;	---------------------------------
; Function test_no_suffix
; ---------------------------------
_test_no_suffix::
	ld	c, e
	ld	b, d
;probe_b_signed_gt_n.c:12: if (a > 8) {
	ld	e, b
	ld	d, #0x00
	ld	a, #0x08
	cp	a, c
	ld	a, #0x00
	sbc	a, b
	bit	7, e
	jr	Z, 00113$
	bit	7, d
	jr	NZ, 00114$
	cp	a, a
	jr	00114$
00113$:
	bit	7, d
	jr	Z, 00114$
	scf
00114$:
	jr	NC, 00102$
;probe_b_signed_gt_n.c:13: result_a = 1u;
	ld	hl, #_result_a
	ld	(hl), #0x01
	ret
00102$:
;probe_b_signed_gt_n.c:15: result_a = 0u;
	xor	a, a
	ld	(#_result_a),a
;probe_b_signed_gt_n.c:17: }
	ret
;probe_b_signed_gt_n.c:19: void test_u_suffix(INT16 a) {
;	---------------------------------
; Function test_u_suffix
; ---------------------------------
_test_u_suffix::
;probe_b_signed_gt_n.c:20: if (a > 8u) {
	ld	a, #0x08
	cp	a, e
	ld	a, #0x00
	sbc	a, d
	jr	NC, 00102$
;probe_b_signed_gt_n.c:21: result_b = 1u;
	ld	hl, #_result_b
	ld	(hl), #0x01
	ret
00102$:
;probe_b_signed_gt_n.c:23: result_b = 0u;
	xor	a, a
	ld	(#_result_b),a
;probe_b_signed_gt_n.c:25: }
	ret
;probe_b_signed_gt_n.c:27: void main(void) {
;	---------------------------------
; Function main
; ---------------------------------
_main::
;probe_b_signed_gt_n.c:28: test_no_suffix(-63);
	ld	de, #0xffc1
	call	_test_no_suffix
;probe_b_signed_gt_n.c:29: test_u_suffix(-63);
	ld	de, #0xffc1
;probe_b_signed_gt_n.c:30: }
	jp	_test_u_suffix
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
