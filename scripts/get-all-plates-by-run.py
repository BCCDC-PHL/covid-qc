#!/usr/bin/env python

import argparse
import collections
import json
import os
import re


def main(args):
    output = []

    all_run_ids = filter(lambda x: re.match('\d{6}_[VM]', x) != None, sorted(list(os.listdir(args.analysis_parent_dir))))

    for run_id in all_run_ids:
        run = collections.OrderedDict()
        artic_qc_path = os.path.join(args.analysis_parent_dir, run_id, 'ncov2019-artic-nf-v' + args.artic_output_version + '-output', run_id + '.qc.csv')
        plate_ids = set()
        with open(artic_qc_path, 'r') as f:
            try:
                next(f)
            except StopIteration as e:
                pass
            for line in f:
                library_id = line.strip().split(',')[0]
                if not (re.match('POS', library_id) or re.match('NEG', library_id)):
                    plate_id = int(library_id.split('-')[1])
                    plate_ids.add(plate_id)
        if plate_ids:
            run['run_id'] = run_id
            run['plate_ids'] = list(plate_ids)
            output.append(run)

    print(json.dumps(output, indent=2))
        

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--analysis-parent-dir', default='/projects/covid-19_production/analysis_by_run')
    parser.add_argument('--artic-output-version', default='1.3')
    args = parser.parse_args()
    main(args)
