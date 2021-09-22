(ns covid-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r] 
            [reagent.dom :as rdom]
            [reagent.dom.server]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ag-grid-react :as ag-grid]
            [ag-charts-react :as ag-chart]
            [cljs.pprint :refer [pprint]]))

(defonce db (r/atom {}))

(def app-version "v0.2.0")

(def url-prefix "")

(defn load-plates-by-run []
  (go (let [response (<! (http/get (str url-prefix "/data/plates_by_run.json")))]
        (swap! db assoc-in [:runs] (:body response)))))

(defn load-ncov-tools-qc-summary [run-id plate-id]
  (go (let [filename (str run-id "_" plate-id "_summary_qc.json")
            path (str url-prefix "/data/ncov-tools-summary/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-plate-ncov-tools-qc-summary] (:body response))
          (swap! db assoc-in [:selected-plate-ncov-tools-qc-summary] nil)))))

(defn load-artic-qc-summary [run-id]
  (go (let [filename (str run-id "_qc.json")
            path (str url-prefix "/data/artic-qc/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-run-artic-qc-summary] (:body response))))))

(defn load-amplicon-coverage [run-id library-id]
  (go (let [filename (str library-id "_amplicon_depth.json")
            path (str url-prefix "/data/ncov-tools-qc-sequencing/" run-id "/" filename)
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
    [:img {:src "images/bccdc_logo.svg" :height "64px"}]]])


(defn get-selected-rows [e]
  (map #(js->clj (.-data %) :keywordize-keys true)
       (-> e
           .-api
           .getSelectedNodes)))

(defn plate-selected [e]
  (do
    (swap! db assoc-in [:selected-plate] (first (get-selected-rows e)))
    (if (:selected-plate @db)
      (do
        (load-artic-qc-summary (:run_id (:selected-plate @db)))
        (load-ncov-tools-qc-summary (:run_id (:selected-plate @db)) (:plate_id (:selected-plate @db))))
      (do
        (swap! db assoc-in [:selected-plate-ncov-tools-qc-summary] nil)
        (swap! db assoc-in [:selected-run-artic-qc-summary] nil))
      
      )))

(defn run-selected [e]
  (do
    (swap! db assoc-in [:selected-runs] (get-selected-rows e))))

(defn library-selected [e]
  (do
    (swap! db assoc-in [:selected-libraries] (get-selected-rows e))
    (if (:selected-libraries @db)
      (do
        (swap! db assoc-in [:selected-amplicon-coverage] nil)
        (doall
         (map #(apply load-amplicon-coverage %) (map (juxt :run_id :library_id) (:selected-libraries @db)))))
      (swap! db assoc-in [:selected-amplicon-coverage] nil))))

(defn expand-run [run]
  (let [run-id (:run_id run)
        plate-ids (:plate_ids run)]
    (map #(assoc {:run_id run-id} :plate_id %) plate-ids)))

(defn get-plates-for-run-id [run-id]
  (mapcat expand-run (filter #(= run-id (:run_id %)) (:runs @db))))

(defn runs-table []
  (let [row-data (map #(assoc {} :run_id (:run_id %)) (:runs @db))]
    [:div {:class "ag-theme-balham"
           :style {:height 256}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "multiple"
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged run-selected}
      [:> ag-grid/AgGridColumn {:field "run_id" :headerName "Run ID" :resizable true :filter "agTextColumnFilter" :sortable true :checkboxSelection true :headerCheckboxSelection true :sort "desc"}]]]))


(defn cell-renderer-hyperlink-button [text params]
  (str "<button><a href=\"" (.-value params) "\" style=\"color: inherit; text-decoration: inherit\" target=\"_blank\">" text "</a></button>"))

(defn cell-renderer-hyperlink-tree [params]
  (cell-renderer-hyperlink-button "Tree" params))

(defn cell-renderer-hyperlink-coverage [params]
  (cell-renderer-hyperlink-button "Coverage" params))

(defn plates-table []
  (let [plates-for-selected-runs (mapcat get-plates-for-run-id (map :run_id (:selected-runs @db)))
        add-tree-link #(assoc % :tree_link (str url-prefix "/data/ncov-tools-plots/tree-snps/" (:run_id %) "_" (:plate_id %) "_tree_snps.pdf"))
        add-coverage-link #(assoc % :coverage_link (str url-prefix "/data/ncov-tools-plots/depth-by-position/" (:run_id %) "_" (:plate_id %) "_depth_by_position.pdf"))
        with-tree-link (map add-tree-link plates-for-selected-runs)
        with-coverage-link (map add-coverage-link with-tree-link)
        row-data with-coverage-link]
    [:div {:class "ag-theme-balham"
           :style {:height 256}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "single"
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged plate-selected
       }
      [:> ag-grid/AgGridColumn {:field "plate_id" :headerName "Plate Number" :filter "agNumberColumnFilter" :sortable true :checkboxSelection true :headerCheckboxSelectionFilteredOnly true :sort "desc"}]
      [:> ag-grid/AgGridColumn {:headerName "Plots"}
       [:> ag-grid/AgGridColumn {:field "tree_link" :headerName "Tree" :maxWidth 100 :cellRenderer cell-renderer-hyperlink-tree}]
       [:> ag-grid/AgGridColumn {:field "coverage_link" :headerName "Coverage" :maxWidth 120 :cellRenderer cell-renderer-hyperlink-coverage}]]]]))


(defn round-number 
   [f]
   (/ (.round js/Math (* 100 f)) 100))

(defn proportion-to-percent [x]
  (* x 100))

(defn library-id-to-well [lib-id]
  (cond
    (re-find #"POS" lib-id) "G12"
    (re-find #"NEG" lib-id) "H12"
    :else (last (clojure.string/split lib-id #"-"))))

(defn libraries-table []
  (let [join-by-comma #(clojure.string/join ", " %)
        selected-ncov-tools-qc (map #(dissoc % :genome_completeness) (:selected-plate-ncov-tools-qc-summary @db))
        selected-artic-qc (filter #(= (:plate_id %) (:plate_id (:selected-plate @db))) (:selected-run-artic-qc-summary @db))
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
        data (map select-data-keys (:selected-plate-ncov-tools-qc-summary @db))
        ct-not-nil #(not (nil? (:qpcr_ct %)))
        percent-completeness (map #(update % :genome_completeness proportion-to-percent) data)
        data-filtered (filter ct-not-nil percent-completeness)]
    [:div
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
        select-data-keys #(select-keys % [:library_id :num_consensus_snvs :num_consensus_iupac])
        data (map select-data-keys (:selected-plate-ncov-tools-qc-summary @db))]
    [:div
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
    [:div
     [:> ag-chart/AgChartsReact {:options {:legend {:enabled true}
                                           :data data
                                           :title {:text "Amplicon Coverage"}
                                           :series [
                                                    {:type "column"
                                                     :xKey "amplicon_num"
                                                     :yKeys (map name samples) :yNames (map name samples)
                                                     :grouped true}]}}]]))

(defn root []
  [:div
   [header]
   [:div {:style {:display "grid"
                  :grid-template-columns "2fr 2fr 6fr"
                  :gap "10px"}}
    [runs-table]
    [plates-table]
    [libraries-table]]
   [:div.plots-container {:style {:display "grid"
                                  :grid-template-columns "1fr 1fr"
                                  :gap "10px"}}
    [completeness-by-ct-plot]
    [variants-histogram-plot]]
   [:div.plots-container {:style {:display "grid"
                                  :grid-template-columns "1fr"
                                  :gap "10px"}}
    [amplicon-coverage-plot]]
   #_[debug-view]
   ]
)

(defn main []
  (load-plates-by-run)
  (rdom/render [root] (js/document.getElementById "app")))

(set! (.-onload js/window) main)

(comment
  
  )
