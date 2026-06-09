;--------------------------------------------------------
; File Created by SDCC : free open source ISO C Compiler
; Version 4.5.1 #15267 (Mac OS X ppc)
;--------------------------------------------------------
	.module probe_f_three_way
	
;--------------------------------------------------------
; Public variables in this module
;--------------------------------------------------------
	.globl _main
	.globl _test_cast
	.globl _test_suffix
	.globl _test_bare
	.globl _rc
	.globl _rb
	.globl _ra
;--------------------------------------------------------
; special function registers
;--------------------------------------------------------
	.area _HRAM
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
	.area _DATA
_ra::
	.ds 1
_rb::
	.ds 1
_rc::
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
;probe_f_three_way.c:6: void test_bare(INT16 a) { if (a < 0) ra = 1u; else ra = 0u; }
;	---------------------------------
; Function test_bare
; ---------------------------------
_test_bare::
	ld	b, d
	bit	7, b
	jr	Z, 00102$
	ld	hl, #_ra
	ld	(hl), #0x01
	ret
00102$:
	xor	a, a
	ld	(#_ra),a
	ret
;probe_f_three_way.c:7: void test_suffix(INT16 a) { if (a < 0u) rb = 1u; else rb = 0u; }
;	---------------------------------
; Function test_suffix
; ---------------------------------
_test_suffix::
	xor	a, a
	ld	(#_rb),a
	ret
;probe_f_three_way.c:8: void test_cast(INT16 a) { if (a < (INT16)0) rc = 1u; else rc = 0u; }
;	---------------------------------
; Function test_cast
; ---------------------------------
_test_cast::
	ld	b, d
	bit	7, b
	jr	Z, 00102$
	ld	hl, #_rc
	ld	(hl), #0x01
	ret
00102$:
	xor	a, a
	ld	(#_rc),a
	ret
;probe_f_three_way.c:10: void main(void) {
;	---------------------------------
; Function main
; ---------------------------------
_main::
;probe_f_three_way.c:11: test_bare(-1);
	ld	de, #0xffff
	call	_test_bare
;probe_f_three_way.c:12: test_suffix(-1);
	ld	de, #0xffff
	call	_test_suffix
;probe_f_three_way.c:13: test_cast(-1);
	ld	de, #0xffff
;probe_f_three_way.c:14: }
	jp	_test_cast
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
