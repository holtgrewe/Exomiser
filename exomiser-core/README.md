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