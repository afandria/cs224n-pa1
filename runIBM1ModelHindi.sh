java -cp classes cs224n.assignments.WordAlignmentTester \
-dataPath /afs/ir/class/cs224n/pa1/data/ \
-language hindi \
-model cs224n.wordaligner.IBM1Model -evalSet dev \
-trainSentences 10000 -verbose
