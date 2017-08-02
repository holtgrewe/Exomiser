The Exomiser - Core Library
===============================================================

Experimental Variant Data storage branch for testing tabix-style variant data. 

Variants were extracted from ExAC, ESP and dbSNP for the frequency data and DBNSFP for the pathogenicity data, split into single alleles and normalised using AllelePosition using the [exomiser-allele-store](https://github.com/julesjacobsen/exomiser_allele_store) code.
 
The data contained 316,337,157 variants/lines/documents. 

Storage | Size on disk (GB) | Total runtime for POMP sample (min)
------------ | ------------- | -----------------
H2 | 28 | 3:40
Tabix | 3 | 11:30
Lucene | 6 | ~45 (got bored waiting) 


H2
============================

A new database was created using VCF format.


```h2
CREATE SCHEMA EXOMISER;

SET SCHEMA EXOMISER;

DROP TABLE IF EXISTS variant;

CREATE TABLE variant (
  chromosome SMALLINT,
  "position" INTEGER,
  rsid       VARCHAR(20),
  ref        VARCHAR(1024),
  alt        VARCHAR(1024),
  info       VARCHAR(512)
);

INSERT INTO variant SELECT * FROM CSVREAD('C:/Users/hhx640/Documents/exomiser-build/data/exomiser-all.pg', 'chromosome|position|rsid|ref|alt|info','charset=UTF-8 fieldDelimiter='' fieldSeparator=| nullString=.');

CREATE INDEX variant_idx
  ON variant (chromosome, "position", ref, alt);

SHUTDOWN COMPACT;
```
e.g.
```
1	74834830	rs45497900	CG	C	ESP_EA=0.2184;ESP_AA=0.0237;ESP_ALL=0.1525;EXAC_AFR=0.043131337;EXAC_FIN=0.23212628;EXAC_NFE=0.17882353;EXAC_OTH=0.113895215;EXAC_SAS=0.018518519
1	74834947	rs79936844	C	T	EXAC_EAS=0.011709602;EXAC_NFE=0.003023706;SIFT=0.0;POLYPHEN=0.999;MUT_TASTER=1.0
```


Hotspots Method | Self Time (%) | Self Time | Self Time (CPU) | Total Time | Total Time (CPU) 
--------------- |-------------- | --------- | --------------- | ---------- | ----------------
org.h2.store.fs.FileDisk.read() |	27.238684 |	71,577 ms (27.2%) |	71,577 ms |	71,577 ms |	71,577 ms
org.h2.store.WriterThread.run() |	14.190871 |	37,290 ms (14.2%) |	0.000 ms |	37,388 ms |	0.000 ms
htsjdk.tribble.readers.TabixReader$IteratorImpl.next() |	3.6980753 |	9,717 ms (3.7%) |	9,717 ms |	31,810 ms |	31,810 ms

Tabix
=============================

Tabix files were created using [htslib 1.5](http://www.htslib.org/download/) on Ubuntu 16.04.
```bash
$ bgzip exomiser-all.vcf
$ tabix -p vcf exomiser-all.vcf.gz
```

bcf format and compressed bcf format required slightly more space but the ```.csi``` index was not readable by the HTSJDK, so tabix was used.   

Hotspots Method | Self Time (%) | Self Time | Self Time (CPU) | Total Time | Total Time (CPU) 
--------------- |-------------- | --------- | --------------- | ---------- | ----------------
htsjdk.tribble.readers.TabixReader$IteratorImpl.next() |	29.630745	 |174,125 ms (29.6%) |	174,125 ms |	483,389 ms |	483,389 ms
htsjdk.samtools.util.BlockGunzipper.unzipBlock() |	20.737188 |	121,862 ms (20.7%) |	121,862 ms |	121,862 ms |	121,862 ms
htsjdk.tribble.readers.TabixReader.getIntv() |	20.664639 |	121,435 ms (20.7%) |	121,435 ms |	121,435 ms |	121,435 ms
htsjdk.samtools.seekablestream.SeekableFileStream.read() |	6.474643 |	38,048 ms (6.5%) |	38,048 ms |	38,048 ms |	38,048 ms
htsjdk.tribble.readers.TabixReader.access$400() |	3.8276968 |	22,493 ms (3.8%) |	22,493 ms |	143,929 ms |	143,929 ms


Conclusions
====

H2 is fast, but large, Tabix is slow but compact! Will stick with H2 for the time being as it seems best to aim for speed of analysis.

Second Approach - Two Tabix Sources
====
Using a slightly easier approach where the variant and frequency tables were replaced with a pathogenicity and frequency tabix file and the DAOs were replaced with a Tabix implementation.

Storage | Size on disk (GB) | Total runtime for POMP sample (min)
------------ | ------------- | -----------------
H2 | 28 | 3:40 (2:40 variant loading/filtering)
Tabix | 3.2 (freq=2.5, path=0.6) | 8:40 (7:40 variant loading/filtering)

This seems like a reasonable compromise, its about half the speed of H2, but the storage space makes up for it. For gnomAD, we might not have the choice. 

It might also be worth splitting the frequencies into separate chromosome tabix files to improve search speed and space could be further improved by using a BCF-lite approach in the INFO field and using the enum ordinal instead of the string representation.

 
Producing the Tabix files
====
Pathogenicity:
```bash
$ java -XX:+UseG1GC -Xmx6G -jar exomiser_allele_store-0.0.2.jar --working-dir=. --loadDbNsfp=dbNSFPv3.4a.zip --out=exomiser-path.vcf
2017-08-02 11:43:04.574  INFO 10968 --- [           main] o.m.e.a.AlleleStoreApplication           : Starting AlleleStoreApplication
2017-08-02 12:06:20.248  INFO 10968 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Finished - processed 81191461 variants total in 1393 sec
2017-08-02 12:06:20.249  INFO 10968 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Merging alleles to file exomiser-path.vcf
2017-08-02 12:21:03.761  INFO 10968 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Done
2017-08-02 12:21:03.765  INFO 10968 --- [           main] o.m.e.a.AlleleStoreApplication           : Started AlleleStoreApplication in 2280.238 seconds (JVM running for 2280.969)

$ bgzip exomiser-path.vcf
$ tabix -p vcf exomiser-path.vcf.gz
```
Frequencies:
```bash
$ java -XX:+UseG1GC -Xmx10G -jar exomiser_allele_store-0.0.2.jar -working-dir=. --loadDbSnp=00-All.vcf.gz --loadExac=ExAC.r0.3.1.sites.vep.vcf.gz --loadEsp=ESP6500SI-V2-SSA137.GRCh38-liftover.snps_indels.vcf.tar.gz --out=exomiser-freq.vcf
2017-08-02 13:20:47.137  INFO 18088 --- [           main] o.m.e.a.AlleleStoreApplication           : Starting AlleleStoreApplication
2017-08-02 13:20:48.827  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Loading ExAC
2017-08-02 13:26:12.088  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Finished - processed 10195872 variants total in 323 sec
2017-08-02 13:26:12.089  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Loading dbSNP
2017-08-02 13:48:31.290  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Finished - processed 252387198 variants total in 1339 sec
2017-08-02 13:48:31.290  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Loading ESP
2017-08-02 13:49:31.006  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Finished - processed 254385402 variants total in 59 sec
2017-08-02 13:49:31.006  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Merging alleles to file exomiser-freq.vcf
2017-08-02 14:49:06.495  INFO 18088 --- [           main] o.m.exomiser.allelestore.AlleleImporter  : Done
2017-08-02 14:49:06.516  INFO 18088 --- [           main] o.m.e.a.AlleleStoreApplication           : Started AlleleStoreApplication in 5300.418 seconds (JVM running for 5301.153)

$ bgzip exomiser-freq.vcf
$ tabix -p vcf exomiser-freq.vcf.gz
```

 POMP sample analysis script used
 =====
```yaml
     ---
     analysis:
         vcf: data/NA19722_601952_AUTOSOMAL_RECESSIVE_POMP_13_29233225_5UTR_38.vcf.gz
         ped:
         modeOfInheritance: AUTOSOMAL_RECESSIVE
         analysisMode: PASS_ONLY 
         hpoIds: ['HP:0000982',
                 'HP:0001036',
                 'HP:0001367',
                 'HP:0001795',
                 'HP:0007465',
                 'HP:0007479',
                 'HP:0007490',
                 'HP:0008064',
                 'HP:0008404',
                 'HP:0009775']
         frequencySources: [
             THOUSAND_GENOMES,
             ESP_AFRICAN_AMERICAN, ESP_EUROPEAN_AMERICAN, ESP_ALL,
             EXAC_AFRICAN_INC_AFRICAN_AMERICAN, EXAC_AMERICAN,
             EXAC_SOUTH_ASIAN, EXAC_EAST_ASIAN,
             EXAC_FINNISH, EXAC_NON_FINNISH_EUROPEAN,
             EXAC_OTHER
             ]
         pathogenicitySources: [POLYPHEN, MUTATION_TASTER, SIFT, REMM]
         #this is the recommended order for a genome-sized analysis.
         steps: [ 
             hiPhivePrioritiser: {},
             priorityScoreFilter: {priorityType: HIPHIVE_PRIORITY, minPriorityScore: 0.501},
             variantEffectFilter: {remove: [SYNONYMOUS_VARIANT]},
             regulatoryFeatureFilter: {},
             frequencyFilter: {maxFrequency: 1.0},
             pathogenicityFilter: {keepNonPathogenic: true},
             inheritanceFilter: {},
             omimPrioritiser: {}
          ]
```