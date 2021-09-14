#!/usr/bin/env python

import argparse
import collections
import csv
import json
import os
import re


def parse_ncov_tools_summary_qc(ncov_tools_summary_qc_path):
    output = []
    all_input_fields = [
        "sample",
        "run_name",
        "num_consensus_snvs",
        "num_consensus_n",
        "num_consensus_iupac",
        "num_variants_snvs",
        "num_variants_indel",
        "num_variants_indel_triplet",
        "mean_sequencing_depth",
        "median_sequencing_depth",
        "qpcr_ct",
        "collection_date",
        "num_weeks",
        "scaled_variants_snvs",
        "genome_completeness",
        "qc_pass",
        "lineage",
        "lineage_notes",
        "watch_mutations",
    ]

    int_fields = [
        'num_consensus_snvs',
        'num_consensus_n',
        'num_consensus_iupac',
        'num_variants_snvs',
        'num_variants_indel',
        'num_variants_indel_triplet',
        'median_sequencing_depth',
        'num_weeks',
    ]

    float_fields = [
        'mean_sequencing_depth',
        'qpcr_ct',
        'scaled_variants_snvs',
        'genome_completeness',
    ]
    with open(ncov_tools_summary_qc_path, 'r') as f:
        reader = csv.DictReader(f, dialect='excel-tab')
        for row in reader:
            qc = collections.OrderedDict()
            for field in all_input_fields:
                if row[field] == 'NA':
                    qc[field] = None
                elif field in int_fields:
                    qc[field] = int(row[field])
                elif field in float_fields:
                    qc[field] = float(row[field])
                elif field == 'sample':
                    qc['library_id'] = row[field]
                elif field == 'run_name':
                    qc['plate_id'] = int(row[field].split('_')[-1])
                    qc['run_id'] = '_'.join(row[field].split('_')[0:-1])
                elif field == 'qc_pass':
                    qc[field] = row[field].split(',')
                else:
                    qc[field] = row[field]
            output.append(qc)

    return output


def main(args):

    ncov_tools_summary_qc = parse_ncov_tools_summary_qc(args.ncov_tools_summary_qc)
    print(json.dumps(ncov_tools_summary_qc, indent=2))
        

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('ncov_tools_summary_qc')
    args = parser.parse_args()
    main(args)
