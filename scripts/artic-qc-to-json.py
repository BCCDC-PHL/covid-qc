#!/usr/bin/env python

import argparse
import collections
import csv
import json
import os
import re


def parse_artic_qc(artic_qc_path, run_id):
    output = []
    all_input_fields = [
        "sample_name",
        "pct_N_bases",
        "pct_covered_bases",
        "longest_no_N_run",
        "num_aligned_reads",
        "fasta",
        "bam",
        "qc_pass",
    ]

    int_fields = [
        'longest_no_N_run',
        'num_aligned_reads',
    ]

    float_fields = [
        'pct_N_bases',
        'pct_covered_bases',
    ]

    boolean_fields = [
        'qc_pass',
    ]
    
    with open(artic_qc_path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            qc = collections.OrderedDict()
            for field in all_input_fields:
                if row[field] == 'NA':
                    qc[field] = None
                elif field == 'sample_name':
                    library_id = row[field]
                    qc['library_id'] = library_id
                    plate_id = None
                    if library_id.startswith('POS') or library_id.startswith('NEG'):
                        plate_id = re.search("\d+", library_id.split('-')[2]).group(0)
                    else:
                        plate_id = re.search("\d+", library_id.split('-')[1]).group(0)
                    qc['plate_id'] = int(plate_id)
                    qc['run_id'] = run_id
                elif field == 'pct_N_bases':
                    pass
                elif field == 'qc_pass':
                    pass
                elif field == 'pct_covered_bases':
                    try:
                        qc['genome_completeness'] = float(row[field])
                    except ValueError as e:
                        qc[field] = None
                elif field in int_fields:
                    try:
                        qc[field] = int(row[field])
                    except ValueError as e:
                        qc[field] = None
                elif field in float_fields:
                    try:
                        qc[field] = float(row[field])
                    except ValueError as e:
                        qc[field] = None
                elif field in boolean_fields:
                    try:
                        qc[field] = bool(row[field])
                    except ValueError as e:
                        qc[field] = None
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

    run_id = os.path.basename(args.artic_qc).split('.')[0]
    artic_qc = parse_artic_qc(args.artic_qc, run_id)
    print(json.dumps(artic_qc, indent=2))
        

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('artic_qc')
    args = parser.parse_args()
    main(args)
