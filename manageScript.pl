#!/usr/bin/perl -w 

#perl script for the management tasks
#The command line input to the script is a config file which contains the following parameters
# 1. name of the .align file to be used for training
# 2. Name of the ini file to be created after training
# 3. max-phrase-length parameter used for training
# 4. distortion limit parameter for tuning
# 5. MERT/PRO used for tuning

# The script does the following tasks
# uses the align file to train the phrase-based translation model
# tunes the model using either MERT/PRO
# decodes the deveelopment test set
# evaluates the performance using BLEU metric
  

my $parameterFile = $ARGV[0];

open FILE, $parameterFile or die "Could not open parameter file";

my @params = <FILE>;

my $alignFile = $params[0];
my $iniFile = $params[1];
my $maxPhraseLen = $params[2];
my $distortionLimit = $params[3];
my $tuningMethod = $params[4];
my $outFile = $params[5];
my $tunedFile = $params[6];
chomp($alignFile);
chomp($iniFile);
chomp($maxPhraseLen);
chomp($distortionLimit);
chomp($tuningMethod);
chomp($outFile);
chomp($tunedFile);
$maxPhraseLen = int($maxPhraseLen);
$distortionLimit = int($distortionLimit);


#first set the enviroment variables 
$ENV{'HOME'}="/afs/ir.stanford.edu/users/a/m/aman313/Documents/cs224n/pa1-mt";
$ENV{'MOSES'}="/afs/ir/class/cs224n/bin/mosesdecoder";
$ENV{'GIZA'}="/afs/ir/class/cs224n/bin/giza-pp-read-only/external-bin-dir";
#print $ENV{'MOSES'}."\n";

#Now train the phrase bases model
$trainCmd= "$ENV{'MOSES'}/scripts/training/train-model.perl --max-phrase-length $maxPhraseLen --external-bin-dir $ENV{'GIZA'} --first-step 4 --last-step 9 -root-dir $ENV{'HOME'}/train -corpus $ENV{'HOME'}/training/corpus -f f -e e -alignment-file $alignFile -alignment align -lm 0:3:"."$ENV{'HOME'}"."/lm.bin:8 --config $iniFile --parallel";
print $trainCmd."\n";

system($trainCmd);


#Now tune the parameters

if($tuningMethod eq "MERT"){
	$tuneCmd = "$ENV{'MOSES'}/scripts/training/mert-moses.pl --working-dir $ENV{'HOME'}/tune --decoder-flags="."\"-distortion-limit $distortionLimit\""." $ENV{'HOME'}/mt-dev.fr $ENV{'HOME'}/mt-dev.en $ENV{'MOSES'}/bin/moses $iniFile --mertdir $ENV{'MOSES'}/bin/ ";

}
else{

	$tuneCmd = "$ENV{'MOSES'}/scripts/training/mert-moses.pl --working-dir $ENV{'HOME'}/tune --decoder-flags="."-distortion-limit $distortionLimit"." $ENV{'HOME'}/mt-dev.fr $ENV{'HOME'}/mt-dev.en $ENV{'MOSES'}/bin/moses $iniFile --mertdir $ENV{'MOSES'}/bin/ --pairwise-ranked --jobs=4 ";
}
print $tuneCmd."\n";
system($tuneCmd);

#Now rename the tuned file
$rnCmd = "mv $ENV{'HOME'}/tune/moses.ini $tunedFile";
system($rnCmd);

#Now decode the dev-test file
$decodeCmd = "cat $ENV{'HOME'}/mt-dev-test.fr | $ENV{'MOSES'}/bin/moses -du -f $iniFile > $outFile";
system($decodeCmd);

#Now evaluate 

