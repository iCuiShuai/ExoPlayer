# 
# 일부 Debug build가 ship된 장비가 있는 것으로 추측되는데,
# 해당 장비들에서는 -O0를 줘야만 OMX decoder에 access할 수 있다.
# 다만, 나머지 optimize option은 사용할 수 있다.
#  -Og, -O1, -O2 모두 crash된다.
#

LOCAL_CFLAGS += \
-O0 \
-fauto-inc-dec \
-fbranch-count-reg \
-fcombine-stack-adjustments \
-fcompare-elim \
-fcprop-registers \
-fdce \
-fdefer-pop \
-fdse \
-fforward-propagate \
-fguess-branch-probability \
-fif-conversion2 \
-fif-conversion \
-finline-functions-called-once \
-fipa-pure-const \
-fipa-profile \
-fipa-reference \
-fmerge-constants \
-fmove-loop-invariants \
-fshrink-wrap \
-fsplit-wide-types \
-ftree-bit-ccp \
-ftree-ccp \
-ftree-ch \
-ftree-copy-prop \
-ftree-copyrename \
-ftree-dce \
-ftree-dominator-opts \
-ftree-dse \
-ftree-forwprop \
-ftree-fre \
-ftree-phiprop \
-ftree-sink \
-ftree-slsr \
-ftree-sra \
-ftree-pta \
-ftree-ter \
-funit-at-a-time\
-fthread-jumps \
-falign-functions \
-falign-jumps \
-falign-loops \
-falign-labels \
-fcaller-saves \
-fcrossjumping \
-fcse-follow-jumps \
-fcse-skip-blocks \
-fdelete-null-pointer-checks \
-fdevirtualize \
-fexpensive-optimizations \
-fgcse \
-fgcse-lm \
-fhoist-adjacent-loads \
-finline-small-functions \
-findirect-inlining \
-fipa-cp \
-fipa-sra \
-foptimize-sibling-calls \
-foptimize-strlen \
-fpartial-inlining \
-fpeephole2 \
-freorder-blocks \
-freorder-functions \
-frerun-cse-after-loop \
-fsched-interblock \
-fsched-spec \
-fschedule-insns \
-fschedule-insns2 \
-fstrict-aliasing \
-fstrict-overflow \
-ftree-builtin-call-dce \
-ftree-switch-conversion \
-ftree-tail-merge \
-ftree-pre \
-ftree-vrp

