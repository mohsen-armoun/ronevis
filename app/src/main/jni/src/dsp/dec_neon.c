// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// ARM NEON version of dsp functions and loop filtering.
//
// Authors: Somnath Banerjee (somnath@google.com)
//          Johann Koenig (johannkoenig@google.com)

#include "./dsp.h"

#if defined(WEBP_USE_NEON)

#include <arm_neon.h>

#include "../dec/vp8i.h"

#define QRegs "q0", "q1", "q2", "q3",                                          \
              "q8", "q9", "q10", "q11", "q12", "q13", "q14", "q15"

#define FLIP_SIGN_BIT2(a, b, s)                                                \
  "veor     " #a "," #a "," #s "               \n"                             \
  "veor     " #b "," #b "," #s "               \n"                             \

#define FLIP_SIGN_BIT4(a, b, c, d, s)                                          \
  FLIP_SIGN_BIT2(a, b, s)                                                      \
  FLIP_SIGN_BIT2(c, d, s)                                                      \

#define NEEDS_FILTER(p1, p0, q0, q1, thresh, mask)                             \
  "vabd.u8    q15," #p0 "," #q0 "         \n"  /* abs(p0 - q0) */              \
  "vabd.u8    q14," #p1 "," #q1 "         \n"  /* abs(p1 - q1) */              \
  "vqadd.u8   q15, q15, q15               \n"  /* abs(p0 - q0) * 2 */          \
  "vshr.u8    q14, q14, #1                \n"  /* abs(p1 - q1) / 2 */          \
  "vqadd.u8   q15, q15, q14     \n"  /* abs(p0 - q0) * 2 + abs(p1 - q1) / 2 */ \
  "vdup.8     q14, " #thresh "            \n"                                  \
  "vcge.u8   " #mask ", q14, q15          \n"  /* mask <= thresh */

#define GET_BASE_DELTA(p1, p0, q0, q1, o)                                      \
  "vqsub.s8   q15," #q0 "," #p0 "         \n"  /* (q0 - p0) */                 \
  "vqsub.s8  " #o "," #p1 "," #q1 "       \n"  /* (p1 - q1) */                 \
  "vqadd.s8  " #o "," #o ", q15           \n"  /* (p1 - q1) + 1 * (p0 - q0) */ \
  "vqadd.s8  " #o "," #o ", q15           \n"  /* (p1 - q1) + 2 * (p0 - q0) */ \
  "vqadd.s8  " #o "," #o ", q15           \n"  /* (p1 - q1) + 3 * (p0 - q0) */

#define DO_SIMPLE_FILTER(p0, q0, fl)                                           \
  "vmov.i8    q15, #0x03                  \n"                                  \
  "vqadd.s8   q15, q15, " #fl "           \n"  /* filter1 = filter + 3 */      \
  "vshr.s8    q15, q15, #3                \n"  /* filter1 >> 3 */              \
  "vqadd.s8  " #p0 "," #p0 ", q15         \n"  /* p0 += filter1 */             \
                                                                               \
  "vmov.i8    q15, #0x04                  \n"                                  \
  "vqadd.s8   q15, q15, " #fl "           \n"  /* filter1 = filter + 4 */      \
  "vshr.s8    q15, q15, #3                \n"  /* filter2 >> 3 */              \
  "vqsub.s8  " #q0 "," #q0 ", q15         \n"  /* q0 -= filter2 */

// Applies filter on 2 pixels (p0 and q0)
#define DO_FILTER2(p1, p0, q0, q1, thresh)                                     \
  NEEDS_FILTER(p1, p0, q0, q1, thresh, q9)     /* filter mask in q9 */         \
  "vmov.i8    q10, #0x80                  \n"  /* sign bit */                  \
  FLIP_SIGN_BIT4(p1, p0, q0, q1, q10)          /* convert to signed value */   \
  GET_BASE_DELTA(p1, p0, q0, q1, q11)          /* get filter level  */         \
  "vand       q9, q9, q11                 \n"  /* apply filter mask */         \
  DO_SIMPLE_FILTER(p0, q0, q9)                 /* apply filter */              \
  FLIP_SIGN_BIT2(p0, q0, q10)

// Load/Store vertical edge
#define LOAD8x4(c1, c2, c3, c4, b1, b2, stride)                                \
  "vld4.8   {" #c1"[0], " #c2"[0], " #c3"[0], " #c4"[0]}," #b1 "," #stride"\n" \
  "vld4.8   {" #c1"[1], " #c2"[1], " #c3"[1], " #c4"[1]}," #b2 "," #stride"\n" \
  "vld4.8   {" #c1"[2], " #c2"[2], " #c3"[2], " #c4"[2]}," #b1 "," #stride"\n" \
  "vld4.8   {" #c1"[3], " #c2"[3], " #c3"[3], " #c4"[3]}," #b2 "," #stride"\n" \
  "vld4.8   {" #c1"[4], " #c2"[4], " #c3"[4], " #c4"[4]}," #b1 "," #stride"\n" \
  "vld4.8   {" #c1"[5], " #c2"[5], " #c3"[5], " #c4"[5]}," #b2 "," #stride"\n" \
  "vld4.8   {" #c1"[6], " #c2"[6], " #c3"[6], " #c4"[6]}," #b1 "," #stride"\n" \
  "vld4.8   {" #c1"[7], " #c2"[7], " #c3"[7], " #c4"[7]}," #b2 "," #stride"\n"

#define STORE8x2(c1, c2, p, stride)                                            \
  "vst2.8   {" #c1"[0], " #c2"[0]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[1], " #c2"[1]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[2], " #c2"[2]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[3], " #c2"[3]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[4], " #c2"[4]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[5], " #c2"[5]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[6], " #c2"[6]}," #p "," #stride " \n"                      \
  "vst2.8   {" #c1"[7], " #c2"[7]}," #p "," #stride " \n"

//-----------------------------------------------------------------------------
// Simple In-loop filtering (Paragraph 15.2)

static void SimpleVFilter16NEON(uint8_t* p, int stride, int thresh) {
  __asm__ volatile (
    "sub        %[p], %[p], %[stride], lsl #1  \n"  // p -= 2 * stride

    "vld1.u8    {q1}, [%[p]], %[stride]        \n"  // p1
    "vld1.u8    {q2}, [%[p]], %[stride]        \n"  // p0
    "vld1.u8    {q3}, [%[p]], %[stride]        \n"  // q0
    "vld1.u8    {q12}, [%[p]]                  \n"  // q1

    DO_FILTER2(q1, q2, q3, q12, %[thresh])

    "sub        %[p], %[p], %[stride], lsl #1  \n"  // p -= 2 * stride

    "vst1.u8    {q2}, [%[p]], %[stride]        \n"  // store op0
    "vst1.u8    {q3}, [%[p]]                   \n"  // store oq0
    : [p] "+r"(p)
    : [stride] "r"(stride), [thresh] "r"(thresh)
    : "memory", QRegs
  );
}

static void SimpleHFilter16NEON(uint8_t* p, int stride, int thresh) {
  __asm__ volatile (
    "sub        r4, %[p], #2                   \n"  // base1 = p - 2
    "lsl        r6, %[stride], #1              \n"  // r6 = 2 * stride
    "add        r5, r4, %[stride]              \n"  // base2 = base1 + stride

    LOAD8x4(d2, d3, d4, d5, [r4], [r5], r6)
    LOAD8x4(d24, d25, d26, d27, [r4], [r5], r6)
    "vswp       d3, d24                        \n"  // p1:q1 p0:q3
    "vswp       d5, d26                        \n"  // q0:q2 q1:q4
    "vswp       q2, q12                        \n"  // p1:q1 p0:q2 q0:q3 q1:q4

    DO_FILTER2(q1, q2, q12, q13, %[thresh])

    "sub        %[p], %[p], #1                 \n"  // p - 1

    "vswp        d5, d24                       \n"
    STORE8x2(d4, d5, [%[p]], %[stride])
    STORE8x2(d24, d25, [%[p]], %[stride])

    : [p] "+r"(p)
    : [stride] "r"(stride), [thresh] "r"(thresh)
    : "memory", "r4", "r5", "r6", QRegs
  );
}

static void SimpleVFilter16iNEON(uint8_t* p, int stride, int thresh) {
  int k;
  for (k = 3; k > 0; --k) {
    p += 4 * stride;
    SimpleVFilter16NEON(p, stride, thresh);
  }
}

static void SimpleHFilter16iNEON(uint8_t* p, int stride, int thresh) {
  int k;
  for (k = 3; k > 0; --k) {
    p += 4;
    SimpleHFilter16NEON(p, stride, thresh);
  }
}

//-----------------------------------------------------------------------------
// Inverse transforms (Paragraph 14.4)

static void TransformOne(const int16_t* in, uint8_t* dst) {
  const int kBPS = BPS;
  const int16_t constants[] = {20091, 17734, 0, 0};
  /* kC1, kC2. Padded because vld1.16 loads 8 bytes
   * Technically these are unsigned but vqdmulh is only available in signed.
   * vqdmulh returns high half (effectively >> 16) but also doubles the value,
   * changing the >> 16 to >> 15 and requiring an additional >> 1.
   * We use this to our advantage with kC2. The canonical value is 35468.
   * However, the high bit is set so treating it as signed will give incorrect
   * results. We avoid this by down shifting by 1 here to clear the highest bit.
   * Combined with the doubling effect of vqdmulh we get >> 16.
   * This can not be applied to kC1 because the lowest bit is set. Down shifting
   * the constant would reduce precision.
   */

  /* libwebp uses a trick to avoid some extra addition that libvpx does.
   * Instead of:
   * temp2 = ip[12] + ((ip[12] * cospi8sqrt2minus1) >> 16);
   * libwebp adds 1 << 16 to cospi8sqrt2minus1 (kC1). However, this causes the
   * same issue with kC1 and vqdmulh that we work around by down shifting kC2
   */

  /* Adapted from libvpx: vp8/common/arm/neon/shortidct4x4llm_neon.asm */
  __asm__ volatile (
    "vld1.16         {q1, q2}, [%[in]]           \n"
    "vld1.16         {d0}, [%[constants]]        \n"

    /* d2: in[0]
     * d3: in[8]
     * d4: in[4]
     * d5: in[12]
     */
    "vswp            d3, d4                      \n"

    /* q8 = {in[4], in[12]} * kC1 * 2 >> 16
     * q9 = {in[4], in[12]} * kC2 >> 16
     */
    "vqdmulh.s16     q8, q2, d0[0]               \n"
    "vqdmulh.s16     q9, q2, d0[1]               \n"

    /* d22 = a = in[0] + in[8]
     * d23 = b = in[0] - in[8]
     */
    "vqadd.s16       d22, d2, d3                 \n"
    "vqsub.s16       d23, d2, d3                 \n"

    /* The multiplication should be x * kC1 >> 16
     * However, with vqdmulh we get x * kC1 * 2 >> 16
     * (multiply, double, return high half)
     * We avoided this in kC2 by pre-shifting the constant.
     * q8 = in[4]/[12] * kC1 >> 16
     */
    "vshr.s16        q8, q8, #1                  \n"

    /* Add {in[4], in[12]} back after the multiplication. This is handled by
     * adding 1 << 16 to kC1 in the libwebp C code.
     */
    "vqadd.s16       q8, q2, q8                  \n"

    /* d20 = c = in[4]*kC2 - in[12]*kC1
     * d21 = d = in[4]*kC1 + in[12]*kC2
     */
    "vqsub.s16       d20, d18, d17               \n"
    "vqadd.s16       d21, d19, d16               \n"

    /* d2 = tmp[0] = a + d
     * d3 = tmp[1] = b + c
     * d4 = tmp[2] = b - c
     * d5 = tmp[3] = a - d
     */
    "vqadd.s16       d2, d22, d21                \n"
    "vqadd.s16       d3, d23, d20                \n"
    "vqsub.s16       d4, d23, d20                \n"
    "vqsub.s16       d5, d22, d21                \n"

    "vzip.16         q1, q2                      \n"
    "vzip.16         q1, q2                      \n"

    "vswp            d3, d4                      \n"

    /* q8 = {tmp[4], tmp[12]} * kC1 * 2 >> 16
     * q9 = {tmp[4], tmp[12]} * kC2 >> 16
     */
    "vqdmulh.s16     q8, q2, d0[0]               \n"
    "vqdmulh.s16     q9, q2, d0[1]               \n"

    /* d22 = a = tmp[0] + tmp[8]
     * d23 = b = tmp[0] - tmp[8]
     */
    "vqadd.s16       d22, d2, d3                 \n"
    "vqsub.s16       d23, d2, d3                 \n"

    /* See long winded explanations prior */
    "vshr.s16        q8, q8, #1                  \n"
    "vqadd.s16       q8, q2, q8                  \n"

    /* d20 = c = in[4]*kC2 - in[12]*kC1
     * d21 = d = in[4]*kC1 + in[12]*kC2
     */
    "vqsub.s16       d20, d18, d17               \n"
    "vqadd.s16       d21, d19, d16               \n"

    /* d2 = tmp[0] = a + d
     * d3 = tmp[1] = b + c
     * d4 = tmp[2] = b - c
     * d5 = tmp[3] = a - d
     */
    "vqadd.s16       d2, d22, d21                \n"
    "vqadd.s16       d3, d23, d20                \n"
    "vqsub.s16       d4, d23, d20                \n"
    "vqsub.s16       d5, d22, d21                \n"

    "vld1.32         d6[0], [%[dst]], %[kBPS]    \n"
    "vld1.32         d6[1], [%[dst]], %[kBPS]    \n"
    "vld1.32         d7[0], [%[dst]], %[kBPS]    \n"
    "vld1.32         d7[1], [%[dst]], %[kBPS]    \n"

    "sub         %[dst], %[dst], %[kBPS], lsl #2 \n"

    /* (val) + 4 >> 3 */
    "vrshr.s16       d2, d2, #3                  \n"
    "vrshr.s16       d3, d3, #3                  \n"
    "vrshr.s16       d4, d4, #3                  \n"
    "vrshr.s16       d5, d5, #3                  \n"

    "vzip.16         q1, q2                      \n"
    "vzip.16         q1, q2                      \n"

    /* Must accumulate before saturating */
    "vmovl.u8        q8, d6                      \n"
    "vmovl.u8        q9, d7                      \n"

    "vqadd.s16       q1, q1, q8                  \n"
    "vqadd.s16       q2, q2, q9                  \n"

    "vqmovun.s16     d0, q1                      \n"
    "vqmovun.s16     d1, q2                      \n"

    "vst1.32         d0[0], [%[dst]], %[kBPS]    \n"
    "vst1.32         d0[1], [%[dst]], %[kBPS]    \n"
    "vst1.32         d1[0], [%[dst]], %[kBPS]    \n"
    "vst1.32         d1[1], [%[dst]]             \n"

    : [in] "+r"(in), [dst] "+r"(dst)  /* modified registers */
    : [kBPS] "r"(kBPS), [constants] "r"(constants)  /* constants */
    : "memory", "q0", "q1", "q2", "q8", "q9", "q10", "q11"  /* clobbered */
  );
}

static void TransformTwo(const int16_t* in, uint8_t* dst, int do_two) {
  TransformOne(in, dst);
  if (do_two) {
    TransformOne(in + 16, dst + 4);
  }
}

static void TransformDC(const int16_t* in, uint8_t* dst) {
  const int DC = (in[0] + 4) >> 3;
  const int kBPS = BPS;
  __asm__ volatile (
    "vdup.16         q1, %[DC]        \n"

    "vld1.32         d0[0], [%[dst]], %[kBPS]    \n"
    "vld1.32         d1[0], [%[dst]], %[kBPS]    \n"
    "vld1.32         d0[1], [%[dst]], %[kBPS]    \n"
    "vld1.32         d1[1], [%[dst]], %[kBPS]    \n"

    "sub         %[dst], %[dst], %[kBPS], lsl #2 \n"

    // add DC and convert to s16.
    "vaddw.u8        q2, q1, d0                  \n"
    "vaddw.u8        q3, q1, d1                  \n"
    // convert back to u8 with saturation
    "vqmovun.s16     d0,  q2                     \n"
    "vqmovun.s16     d1,  q3                     \n"

    "vst1.32         d0[0], [%[dst]], %[kBPS]    \n"
    "vst1.32         d1[0], [%[dst]], %[kBPS]    \n"
    "vst1.32         d0[1], [%[dst]], %[kBPS]    \n"
    "vst1.32         d1[1], [%[dst]]             \n"
    : [in] "+r"(in), [dst] "+r"(dst)  /* modified registers */
    : [kBPS] "r"(kBPS),   /* constants */
      [DC] "r"(DC)
    : "memory", "q0", "q1", "q2", "q3"  /* clobbered */
  );
}

static void TransformWHT(const int16_t* in, int16_t* out) {
  const int kStep = 32;  // The store is only incrementing the pointer as if we
                         // had stored a single byte.
  __asm__ volatile (
    // part 1
    // load data into q0, q1
    "vld1.16         {q0, q1}, [%[in]]           \n"

    "vaddl.s16       q2, d0, d3                  \n"  // a0 = in[0] + in[12]
    "vaddl.s16       q3, d1, d2                  \n"  // a1 = in[4] + in[8]
    "vsubl.s16       q10, d1, d2                 \n"  // a2 = in[4] - in[8]
    "vsubl.s16       q11, d0, d3                 \n"  // a3 = in[0] - in[12]

    "vadd.s32        q0, q2, q3                  \n"  // tmp[0] = a0 + a1
    "vsub.s32        q2, q2, q3                  \n"  // tmp[8] = a0 - a1
    "vadd.s32        q1, q11, q10                \n"  // tmp[4] = a3 + a2
    "vsub.s32        q3, q11, q10                \n"  // tmp[12] = a3 - a2

    // Transpose
    // q0 = tmp[0, 4, 8, 12], q1 = tmp[2, 6, 10, 14]
    // q2 = tmp[1, 5, 9, 13], q3 = tmp[3, 7, 11, 15]
    "vswp            d1, d4                      \n"  // vtrn.64 q0, q2
    "vswp            d3, d6                      \n"  // vtrn.64 q1, q3
    "vtrn.32         q0, q1                      \n"
    "vtrn.32         q2, q3                      \n"

    "vmov.s32        q10, #3                     \n"  // dc = 3
    "vadd.s32        q0, q0, q10                 \n"  // dc = tmp[0] + 3
    "vadd.s32        q12, q0, q3                 \n"  // a0 = dc + tmp[3]
    "vadd.s32        q13, q1, q2                 \n"  // a1 = tmp[1] + tmp[2]
    "vsub.s32        q8, q1, q2                  \n"  // a2 = tmp[1] - tmp[2]
    "vsub.s32        q9, q0, q3                  \n"  // a3 = dc - tmp[3]

    "vadd.s32        q0, q12, q13                \n"
    "vshrn.s32       d0, q0, #3                  \n"  // (a0 + a1) >> 3
    "vadd.s32        q1, q9, q8                  \n"
    "vshrn.s32       d1, q1, #3                  \n"  // (a3 + a2) >> 3
    "vsub.s32        q2, q12, q13                \n"
    "vshrn.s32       d2, q2, #3                  \n"  // (a0 - a1) >> 3
    "vsub.s32        q3, q9, q8                  \n"
    "vshrn.s32       d3, q3, #3                  \n"  // (a3 - a2) >> 3

    // set the results to output
    "vst1.16         d0[0], [%[out]], %[kStep]   \n"
    "vst1.16         d1[0], [%[out]], %[kStep]   \n"
    "vst1.16         d2[0], [%[out]], %[kStep]   \n"
    "vst1.16         d3[0], [%[out]], %[kStep]   \n"
    "vst1.16         d0[1], [%[out]], %[kStep]   \n"
    "vst1.16         d1[1], [%[out]], %[kStep]   \n"
    "vst1.16         d2[1], [%[out]], %[kStep]   \n"
    "vst1.16         d3[1], [%[out]], %[kStep]   \n"
    "vst1.16         d0[2], [%[out]], %[kStep]   \n"
    "vst1.16         d1[2], [%[out]], %[kStep]   \n"
    "vst1.16         d2[2], [%[out]], %[kStep]   \n"
    "vst1.16         d3[2], [%[out]], %[kStep]   \n"
    "vst1.16         d0[3], [%[out]], %[kStep]   \n"
    "vst1.16         d1[3], [%[out]], %[kStep]   \n"
    "vst1.16         d2[3], [%[out]], %[kStep]   \n"
    "vst1.16         d3[3], [%[out]], %[kStep]   \n"

    : [out] "+r"(out)  // modified registers
    : [in] "r"(in), [kStep] "r"(kStep)  // constants
    : "memory", "q0", "q1", "q2", "q3",
      "q8", "q9", "q10", "q11", "q12", "q13"  // clobbered
  );
}

//------------------------------------------------------------------------------

#define MUL(a, b) (((a) * (b)) >> 16)
static void TransformAC3(const int16_t* in, uint8_t* dst) {
  static const int kC1 = 20091 + (1 << 16);
  static const int kC2 = 35468;
  const int16x8_t A = vdupq_n_s16(in[0] + 4);
  const int16x8_t c4 = vdupq_n_s16(MUL(in[4], kC2));
  const int16x8_t d4 = vdupq_n_s16(MUL(in[4], kC1));
  const int c1 = MUL(in[1], kC2);
  const int d1 = MUL(in[1], kC1);
  const int16x8_t CD = {d1, c1, -c1, -d1, 0, 0, 0, 0};
  const int16x8_t B = vqaddq_s16(A, CD);
  const int16x8_t m0 = vqaddq_s16(B, d4);
  const int16x8_t m1 = vqaddq_s16(B, c4);
  const int16x8_t m2 = vqsubq_s16(B, c4);
  const int16x8_t m3 = vqsubq_s16(B, d4);
  // Load the source pixels and convert to 16b.
  int16x8_t dst0 = vreinterpretq_s16_u16(
      vmovl_u8(vcreate_u8(*(uint32_t*)(dst + 0 * BPS))));
  int16x8_t dst1 = vreinterpretq_s16_u16(
      vmovl_u8(vcreate_u8(*(uint32_t*)(dst + 1 * BPS))));
  int16x8_t dst2 = vreinterpretq_s16_u16(
      vmovl_u8(vcreate_u8(*(uint32_t*)(dst + 2 * BPS))));
  int16x8_t dst3 = vreinterpretq_s16_u16(
      vmovl_u8(vcreate_u8(*(uint32_t*)(dst + 3 * BPS))));

  // Add the inverse transform.
  dst0 = vsraq_n_s16(dst0, m0, 3);
  dst1 = vsraq_n_s16(dst1, m1, 3);
  dst2 = vsraq_n_s16(dst2, m2, 3);
  dst3 = vsraq_n_s16(dst3, m3, 3);

  {
    // Unsigned saturate to 8b.
    const uint8x8_t dst0_ = vqmovun_s16(dst0);
    const uint8x8_t dst1_ = vqmovun_s16(dst1);
    const uint8x8_t dst2_ = vqmovun_s16(dst2);
    const uint8x8_t dst3_ = vqmovun_s16(dst3);

    // Store the results.
    *(int*)(dst + 0 * BPS) = vget_lane_s32(vreinterpret_s32_u8(dst0_), 0);
    *(int*)(dst + 1 * BPS) = vget_lane_s32(vreinterpret_s32_u8(dst1_), 0);
    *(int*)(dst + 2 * BPS) = vget_lane_s32(vreinterpret_s32_u8(dst2_), 0);
    *(int*)(dst + 3 * BPS) = vget_lane_s32(vreinterpret_s32_u8(dst3_), 0);
  }
}
#undef MUL

#endif   // WEBP_USE_NEON

//------------------------------------------------------------------------------
// Entry point

extern void VP8DspInitNEON(void);

void VP8DspInitNEON(void) {
#if defined(WEBP_USE_NEON)
  VP8Transform = TransformTwo;
  VP8TransformAC3 = TransformAC3;
  VP8TransformDC = TransformDC;
  VP8TransformWHT = TransformWHT;

  VP8SimpleVFilter16 = SimpleVFilter16NEON;
  VP8SimpleHFilter16 = SimpleHFilter16NEON;
  VP8SimpleVFilter16i = SimpleVFilter16iNEON;
  VP8SimpleHFilter16i = SimpleHFilter16iNEON;
#endif   // WEBP_USE_NEON
}
