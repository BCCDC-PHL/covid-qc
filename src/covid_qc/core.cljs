(ns covid-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.set]
            [reagent.core :as r] 
            [reagent.dom :as rdom]
            [reagent.dom.server]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ag-grid-react :as ag-grid]
            [ag-charts-react :as ag-chart]
            [cljs.pprint :refer [pprint]]))

(defonce db (r/atom {}))


(def app-version "v0.3.0")


(def url-prefix "")


(def genome-completeness-qc-threshold 0.85)


(defn mean [coll]
  "Calculate the artithmetic mean of a collection of numbers"
  (/ (reduce + coll) (count coll)))


(defn round-number 
   [f]
   (/ (.round js/Math (* 100 f)) 100))


(defn proportion-to-percent [x]
  (* x 100))


(defn in? 
  "true if coll contains elem"
  [coll elem]  
  (some #(= elem %) coll))


(defn load-plates-by-run []
  (go (let [response (<! (http/get (str url-prefix "data/plates_by_run.json")))]
        (swap! db assoc-in [:runs] (:body response)))))


(defn load-ncov-tools-qc-summary [run-id plate-id]
  (go (let [filename (str run-id "_" plate-id "_summary_qc.json")
            path (str url-prefix "data/ncov-tools-summary/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:ncov-tools-qc-summaries plate-id] (:body response))))))


(defn summarize-artic-qc-summary-by-plate [artic-qc-summary plate-id]
  "Take a full artic 'qc' and compute some relevant summary stats for a specific plate"
  (let [artic-qc-summary-for-plate (filter #(= (:plate_id %) plate-id) artic-qc-summary)
        num-libraries (count artic-qc-summary-for-plate)
        failed-libraries (filter #(< (/ (:genome_completeness %) 100) genome-completeness-qc-threshold) artic-qc-summary-for-plate)
        num-failed (count failed-libraries)
        percent-failed (* (/ num-failed num-libraries) 100)]
    {:num_libraries num-libraries
     :percent_failed percent-failed}))


(defn summarize-ncov-tools-qc-summary [ncov-tools-qc-summary]
  "Take a full ncov-tools 'summary_qc' and compute some relevant summary stats"
  (let [num-libraries (count ncov-tools-qc-summary)
        failed-libraries (filter #(< (:genome_completeness %) genome-completeness-qc-threshold) ncov-tools-qc-summary)
        num-failed (count failed-libraries)
        percent-failed (* (/ num-failed num-libraries) 100)
        avail-ct-failed (filter #(and (not (nil? %)) (not (zero? %))) (map :qpcr_ct failed-libraries))
        avg-ct-failed (mean avail-ct-failed)
        num-ct-avail-failed (count avail-ct-failed)
        num-excess-ambig (count (filter #(contains? (set (:qc_pass %)) "EXCESS_AMBIGUITY") ncov-tools-qc-summary))
        percent-excess-ambig (* (/ num-excess-ambig num-libraries) 100)
        avg-median-depth (mean (map :median_sequencing_depth ncov-tools-qc-summary))]
    {:num_libraries num-libraries
     :percent_failed percent-failed
     :percent_excess_ambiguity percent-excess-ambig
     :avg_ct_failed_samples avg-ct-failed
     :num_ct_available_failed_samples num-ct-avail-failed
     :avg_median_depth_coverage avg-median-depth}))


(defn load-artic-qc-summary [run-id]
  (go (let [filename (str run-id "_qc.json")
            path (str url-prefix "data/artic-qc/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:artic-qc-summaries run-id] (:body response))))))


(defn load-amplicon-coverage [run-id library-id]
  (go (let [filename (str library-id "_amplicon_depth.json")
            path (str url-prefix "data/ncov-tools-qc-sequencing/" run-id "/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-amplicon-coverage (keyword library-id)] (:body response))))))


(defn debug-view []
  (let [current-debug (:debug-view @db)
        toggle-debug #(swap! db assoc :debug-view (not current-debug))]
    [:div
     [:button {:on-click toggle-debug} "Toggle Debug View"]
     [:div.debug {:style {:background-color "#CDCDCD" :display (if (:debug-view @db) "block" "none")}}
      [:pre [:code {:style {:font-family "monospace" }}
             (with-out-str (pprint (select-keys @db [:debug-view :selected-runs :selected-plate :selected-libraries :selected-run-artic-qc-summary :selected-plate-ncov-tools-qc-summary])))]]]]))


(defn header []
  [:header {:style {:display "grid"
                    :grid-template-columns "repeat(2, 1fr)"
                    :align-items "center"}}
   [:div {:style {:display "grid"
                  :grid-template-columns "repeat(2, 1fr)"
                  :align-items "center"}}
    [:h1 {:style {:font-family "Arial" :color "#004a87"}} "COVID-19 Genomics QC"][:p {:style {:font-family "Arial" :color "grey" :justify-self "start"}} app-version]]
   [:div {:style {:display "grid" :align-self "center" :justify-self "end"}}
    [:img {:src (str url-prefix "/images/bccdc_logo.svg") :height "48px"}]]])


(defn get-selected-rows [e]
  (map #(js->clj (.-data %) :keywordize-keys true)
       (-> e
           .-api
           .getSelectedNodes)))


(defn expand-run [run]
  "Takes a run with shape {:run_id 'RUNID' :plate_ids [1 2 3]}
   and expands to ({:run_id 'RUNID' :plate_id 1} {:run_id 'RUNID' :plate_id 2} {:run_id 'RUNID' :plate_id 3})"
  (let [run-id (:run_id run)
        plate-ids (:plate_ids run)]
    (map #(assoc {:run_id run-id} :plate_id %) plate-ids)))


(defn plate-selected [e]
  (let [selected-plate-id (:plate_id (first (get-selected-rows e)))]
    (do
      (swap! db assoc-in [:selected-plate-id] selected-plate-id))))


(defn run-selected [e]
  (let [previously-selected-run-ids (:selected-run-ids @db)
        currently-selected-run-ids (map :run_id (get-selected-rows e))
        newly-selected-run-ids (clojure.set/difference (set currently-selected-run-ids) (set previously-selected-run-ids))
        currently-selected-runs (filter #(in? currently-selected-run-ids (:run_id %)) (:runs @db))
        newly-selected-runs (filter #(in? newly-selected-run-ids (:run_id %)) (:runs @db))
        newly-selected-runs-expanded (mapcat expand-run newly-selected-runs) ;; ({:run_id a :plate_id 1} {:run_id a :plate_id 2} {:run_id b :plate_id 1} {:run_id b :plate_id 2})
        currently-loaded-ncov-tools-qc-summary-plate-ids (:ncov-tools-qc-summaries @db)]
    (do
      (doall
       (map #(load-ncov-tools-qc-summary (:run_id %) (:plate_id %)) newly-selected-runs-expanded))
      (doall
       (map load-artic-qc-summary newly-selected-run-ids))
      (swap! db assoc-in [:selected-run-ids] currently-selected-run-ids))))


(defn library-selected [e]
  (do
    (swap! db assoc-in [:selected-libraries] (get-selected-rows e))
    (if (:selected-libraries @db)
      (do
        (swap! db assoc-in [:selected-amplicon-coverage] nil)
        (doall
         (map #(apply load-amplicon-coverage %) (map (juxt :run_id :library_id) (:selected-libraries @db)))))
      (swap! db assoc-in [:selected-amplicon-coverage] nil))))


(defn get-plates-for-run-id [run-id]
  (mapcat expand-run (filter #(= run-id (:run_id %)) (:runs @db))))


(defn runs-table []
  (let [row-data (map #(assoc {} :run_id (:run_id %)
                                 :num_fastq_symlink_pairs (:num_fastq_symlink_pairs %)
                                 :num_covid19_production_samples_in_samplesheet (:num_covid19_production_samples_in_samplesheet %))
                      (:runs @db))]
    [:div {:class "ag-theme-balham"
           :style {:height 256}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "multiple"
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged run-selected}
      [:> ag-grid/AgGridColumn {:field "run_id" :headerName "Run ID" :minWidth 265 :resizable true :filter "agTextColumnFilter" :sortable true :checkboxSelection true :sort "desc"}]
      [:> ag-grid/AgGridColumn {:field "num_fastq_symlink_pairs" :headerName "Fastq Pairs" :maxWidth 100 :resizable true :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "num_covid19_production_samples_in_samplesheet" :headerName "SampleSheet Count" :maxWidth 150 :resizable true :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]]]))


(defn cell-renderer-hyperlink-button [text params]
  (str "<button><a href=\"" (.-value params) "\" style=\"color: inherit; text-decoration: inherit\" target=\"_blank\">" text "</a></button>"))

(defn cell-renderer-hyperlink-tree [params]
  (cell-renderer-hyperlink-button "Tree" params))

(defn cell-renderer-hyperlink-coverage-profile [params]
  (cell-renderer-hyperlink-button "Coverage Profile" params))

(defn cell-renderer-hyperlink-coverage-heatmap [params]
  (cell-renderer-hyperlink-button "Coverage Heatmap" params))

(defn plates-table []
  (let [selected-runs (filter #(in? (:selected-run-ids @db) (:run_id %)) (:runs @db))
        plates-for-selected-runs (mapcat get-plates-for-run-id (map :run_id selected-runs))
        add-tree-link #(assoc % :tree_link (str url-prefix "data/ncov-tools-plots/tree-snps/" (:run_id %) "_" (:plate_id %) "_tree_snps.pdf"))
        add-coverage-link #(assoc % :coverage_profile_link (str url-prefix "data/ncov-tools-plots/depth-by-position/" (:run_id %) "_" (:plate_id %) "_depth_by_position.pdf"))
        add-heatmap-link #(assoc % :coverage_heatmap_link (str url-prefix "data/ncov-tools-plots/depth-heatmap/" (:run_id %) "_" (:plate_id %) "_amplicon_coverage_heatmap.pdf"))
        merge-artic-qc-summary #(merge % (summarize-artic-qc-summary-by-plate (get (:artic-qc-summaries @db) (:run_id %)) (:plate_id %)))
        merge-ncov-tools-qc-summary-summary #(merge % (summarize-ncov-tools-qc-summary (get (:ncov-tools-qc-summaries @db) (:plate_id %))))
        row-data (->> plates-for-selected-runs
                      (map add-tree-link)
                      (map add-coverage-link)
                      (map add-heatmap-link)
                      (map merge-ncov-tools-qc-summary-summary)
                      (map merge-artic-qc-summary)
                      (map #(update % :percent_failed round-number))
                      (map #(update % :percent_excess_ambiguity round-number))
                      (map #(update % :avg_ct_failed_samples round-number))
                      (map #(update % :avg_median_depth_coverage round-number)))]
    [:div {:class "ag-theme-balham"
           :style {:height 256}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "single"
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged plate-selected}
      [:> ag-grid/AgGridColumn {:field "plate_id" :headerName "Plate Number" :maxWidth 145 :filter "agNumberColumnFilter" :sortable true :checkboxSelection true :headerCheckboxSelectionFilteredOnly true :sort "desc"}]
      [:> ag-grid/AgGridColumn {:field "tree_link" :headerName "Tree" :maxWidth 72 :cellRenderer cell-renderer-hyperlink-tree}]
      [:> ag-grid/AgGridColumn {:field "coverage_profile_link" :headerName "Coverage Profile" :maxWidth 130 :cellRenderer cell-renderer-hyperlink-coverage-profile}]
      [:> ag-grid/AgGridColumn {:field "coverage_heatmap_link" :headerName "Coverage Heatmap" :maxWidth 145 :cellRenderer cell-renderer-hyperlink-coverage-heatmap}]
      [:> ag-grid/AgGridColumn {:field "num_libraries" :headerName "Num. Libraries" :maxWidth 125 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "percent_failed" :headerName "% Failed" :maxWidth 95 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "percent_excess_ambiguity" :headerName "% Excess Ambig." :maxWidth 140 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "avg_ct_failed_samples" :headerName "Avg. Ct (Failed)" :maxWidth 130 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "num_ct_available_failed_samples" :headerName "Num. Ct Avail. (Failed)" :maxWidth 165 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "avg_median_depth_coverage" :headerName "Avg. Median Depth" :maxWidth 150 :filter "agNumberColumnFilter" :sortable true :type "numericColumn"}]]]))


(defn library-id-to-well [lib-id]
  (if lib-id
    (cond
      (re-find #"POS" lib-id) "G12"
      (re-find #"NEG" lib-id) "H12"
      :else (last (clojure.string/split lib-id #"-")))
    "-"))

(defn libraries-table []
  (let [join-by-comma #(clojure.string/join ", " %)
        selected-plate-ncov-tools-qc-summary (get (:ncov-tools-qc-summaries @db) (:selected-plate-id @db))
        selected-ncov-tools-qc (map #(dissoc % :genome_completeness) selected-plate-ncov-tools-qc-summary)
        selected-artic-qc (filter #(= (:plate_id %) (:selected-plate-id @db)) (apply concat (vals (:artic-qc-summaries @db))))
        selected-merged-qc (map #(apply merge %) (vals (group-by :library_id (concat selected-ncov-tools-qc selected-artic-qc))))
        added-well (map #(assoc % :well (library-id-to-well (:library_id %))) selected-merged-qc)
        concat-qc-flags (map #(update % :qc_pass join-by-comma) added-well)
        row-data (map #(update % :qpcr_ct round-number) concat-qc-flags)]
    [:div {:class "ag-theme-balham"
           :style {:height 256}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :rowSelection "multiple"
       :floatingFilter true
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged library-selected
       }
      [:> ag-grid/AgGridColumn {:field "library_id" :headerName "Library ID" :maxWidth 200 :sortable true :resizable true :filter "agTextColumnFilter" :pinned "left" :checkboxSelection true :headerCheckboxSelectionFilteredOnly true}]
      [:> ag-grid/AgGridColumn {:field "well" :headerName "Well" :maxWidth 100 :sortable true :resizable true :filter "agTextColumnFilter" :sort "asc"}]
      [:> ag-grid/AgGridColumn {:field "genome_completeness" :maxWidth 140 :headerName "Completeness (%)" :sortable true :resizable true :filter "agNumberColumnFilter" :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "num_aligned_reads" :maxWidth 120 :headerName "Aligned Reads" :sortable true :resizable true :filter "agNumberColumnFilter" :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "qpcr_ct" :maxWidth 100 :headerName "qPCR Ct" :sortable true :resizable true :filter "agNumberColumnFilter" :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "qc_pass" :headerName "QC Flags" :sortable true :resizable true :filter "agTextColumnFilter"}]
      ]]
    ))

(defn completeness-by-ct-plot []
  (let [select-data-keys #(select-keys % [:library_id :qpcr_ct :genome_completeness])
        selected-plate-ncov-tools-qc-summary (get (:ncov-tools-qc-summaries @db) (:selected-plate-id @db))
        data (map select-data-keys selected-plate-ncov-tools-qc-summary)
        ct-not-nil #(not (nil? (:qpcr_ct %)))
        percent-completeness (map #(update % :genome_completeness proportion-to-percent) data)
        data-filtered (filter ct-not-nil percent-completeness)]
    [:div {:style {:border "1px solid lightgrey"}}
     [:> ag-chart/AgChartsReact {:options {:legend {:enabled false}
                                           :data data-filtered
                                           :title {:text "Completeness by qPCR Ct"}
                                           :series [{:type "scatter"
                                                     :labelKey "library_id" :labelName "Library ID"
                                                     :xKey "qpcr_ct" :xName "qPCR Ct"
                                                     :yKey "genome_completeness" :yName "Completeness"}]
                                           :axes [{:type "number" :position "bottom"}
                                                  {:type "number" :position "left"}]}}]]))

(defn variants-histogram-plot []
  (let [bins [[0 1] [1 2] [2 4] [4 6] [6 8] [8 12] [12 14] [14 16] [16 18] [18 20] [20 22] [22 24] [24 26] [26 28] [28 30] [30 32] [32 34] [34 36] [36 38] [38 40] [40 42]]
        select-data-keys #(select-keys % [:library_id :num_consensus_snvs :num_consensus_iupac :num_variants_indel])
        selected-plate-ncov-tools-qc-summary (get (:ncov-tools-qc-summaries @db) (:selected-plate-id @db))
        data (map select-data-keys selected-plate-ncov-tools-qc-summary)]
    [:div {:style {:border "1px solid lightgrey"}}
     [:> ag-chart/AgChartsReact {:options {:legend {:enabled true}
                                           :data data
                                           :title {:text "Variant Histogram"}
                                           :series [
                                                    {:type "histogram"
                                                     :labelKey "library_id" :labelName "Library ID"
                                                     :xKey "num_variants_indel"
                                                     :yName "Indels"
                                                     :bins bins}
                                                    {:type "histogram"
                                                     :labelKey "library_id" :labelName "Library ID"
                                                     :xKey "num_consensus_iupac"
                                                     :yName "Ambiguous"
                                                     :bins bins}
                                                    {:type "histogram"
                                                     :labelKey "library_id" :labelName "Library ID"
                                                     :xKey "num_consensus_snvs"
                                                     :yName "SNVs"
                                                     :bins bins}
                                                    ]
                                           :axes [{:type "number" :position "bottom"}
                                                  {:type "number" :position "left"}]}}]]))


(defn amplicon-coverage-plot []
  (let [selected-coverages (:selected-amplicon-coverage @db)
        select-depth #(get % :mean_depth)
        samples (keys selected-coverages)
        transform #(map (partial assoc {}) (repeat %) (map select-depth %2))
        sample-depths (map #(apply transform %) (into [] selected-coverages))
        amplicons (map #(assoc {} :amplicon_num %) (map str (range 1 30)))
        data (reduce #(map merge % %2) amplicons sample-depths)]
    [:div {:style {:border "1px solid lightgrey"}}
     [:> ag-chart/AgChartsReact {:options {:legend {:enabled true}
                                           :data data
                                           :title {:text "Amplicon Coverage"}
                                           :series [
                                                    {:type "column"
                                                     :xKey "amplicon_num"
                                                     :yKeys (map name samples) :yNames (map name samples)
                                                     :grouped true}]}}]]))

(defn root []
  [:div {:style {:display "grid"
                 :grid-template-columns "1fr"
                 :grid-gap "4px 4px"}}

   [header]

   [:div {:style {:display "grid"
                  :grid-template-columns "2fr 5fr"
                  :gap "4px"}}
    [runs-table]
    [plates-table]]

   [:div.plots-container {:style {:display "grid"
                                  :grid-template-columns "1fr 1fr"
                                  :gap "4px"}}
    [completeness-by-ct-plot]
    [variants-histogram-plot]]

   [:div {:style {:display "grid"
                  :grid-template-columns "1fr 1fr"
                  :gap "4px"}}
    [libraries-table]
    [amplicon-coverage-plot]]
   
   #_[debug-view]
   ])

(defn main []
  (load-plates-by-run)
  (rdom/render [root] (js/document.getElementById "app")))

(set! (.-onload js/window) main)

(comment

  )
