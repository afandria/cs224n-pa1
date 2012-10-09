java -cp classes cs224n.assignments.WordAlignmentTester \
-dataPath /afs/ir/class/cs224n/pa1/data/ \
-model cs224n.wordaligner.IBM2Model -evalSet dev \
-trainSentences 10000 \
-outputAlignments IBM2_10000.align
