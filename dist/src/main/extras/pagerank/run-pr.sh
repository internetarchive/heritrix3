#!/usr/bin/env sh

#assuming normalized input

lines=`cat input/* | wc -l`;

for f in `seq 1 20`; do
sh bin/hadoop jar hadoop-0.14.3-examples.jar pageRank input output

rm input/* 

total=`cat output/* | cut -f2 | ~vinay/tools/sum_col`;
cat output/* | ~vinay/tools/normalizePageRank.pl $total $lines > input/data.pr.$f

#mv output/* input/

rm -rf output/

done
