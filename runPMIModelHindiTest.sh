java -cp classes cs224n.assignments.WordAlignmentTester \
-dataPath /afs/ir/class/cs224n/pa1/data/ \
-language hindi \
-model cs224n.wordaligner.PMIModel -evalSet test \
-trainSentences 200000 -verbose
