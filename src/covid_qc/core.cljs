(ns covid-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.dom.server]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ag-grid-react :as ag-grid]
            [ag-charts-react :as ag-chart]))

(defonce db (r/atom {}))

(def app-version "v0.0.0-alpha")

(def url-prefix "")

(defn load-plates-by-run []
  (go (let [response (<! (http/get (str url-prefix "/data/plates_by_run.json")))]
        (swap! db assoc-in [:runs] (:body response)))))

(defn load-qc-summary [run-id plate-id]
  (go (let [filename (str run-id "_" plate-id "_summary_qc.json")
            path (str url-prefix "/data/ncov-tools-summary/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-plate-qc-summary] (:body response))))))

(defn load-amplicon-coverage [run-id library-id]
  (go (let [filename (str library-id "_amplicon_depth.json")
            path (str url-prefix "/data/ncov-tools-qc-sequencing/" run-id "/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-amplicon-coverage (keyword library-id)] (:body response))))))

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
      (load-qc-summary (:run_id (:selected-plate @db)) (:plate_id (:selected-plate @db)))
      (swap! db assoc-in [:selected-plate-qc-summary] nil)
      )))

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
       :onFirstDataRendered #(. (. % -api) sizeColumnsToFit)
       :onSelectionChanged #(swap! db assoc-in [:selected-runs] (get-selected-rows %))}
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
        selected-qc (:selected-plate-qc-summary @db)
        added-well (map #(assoc % :well (library-id-to-well (:library_id %))) selected-qc)
        concat-qc-flags (map #(update % :qc_pass join-by-comma) added-well)
        truncated-ct (map #(update % :qpcr_ct round-number) concat-qc-flags)
        row-data (map #(update % :genome_completeness (comp round-number proportion-to-percent)) truncated-ct)]
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
      [:> ag-grid/AgGridColumn {:field "genome_completeness" :maxWidth 120 :headerName "Completeness (%)" :sortable true :resizable true :filter "agNumberColumnFilter" :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "qpcr_ct" :maxWidth 120 :headerName "qPCR Ct" :sortable true :resizable true :filter "agNumberColumnFilter" :type "numericColumn"}]
      [:> ag-grid/AgGridColumn {:field "qc_pass" :headerName "QC Flags" :sortable true :resizable true :filter "agTextColumnFilter"}]
      ]]
    ))

(defn completeness-by-ct-plot []
  (let [select-data-keys #(select-keys % [:library_id :qpcr_ct :genome_completeness])
        data (map select-data-keys (:selected-plate-qc-summary @db))
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
        data (map select-data-keys (:selected-plate-qc-summary @db))]
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

;; The following adapted from:
;;https://github.com/weavejester/medley/blob/d723afcb18e1fae27f3b68a25c7a151569159a9e/src/medley/core.cljc#L78-L80
(defn- editable? [coll]
  (satisfies? cljs.core.IEditableCollection coll))

;; The following taken :
;; https://github.com/weavejester/medley/blob/d723afcb18e1fae27f3b68a25c7a151569159a9e/src/medley/core.cljc#L82-L86
(defn- reduce-map [f coll]
  (let [coll' (if (record? coll) (into {} coll) coll)]
    (if (editable? coll')
      (persistent! (reduce-kv (f assoc!) (transient (empty coll')) coll'))
      (reduce-kv (f assoc) (empty coll') coll'))))

;; The following taken :
;; https://github.com/weavejester/medley/blob/d723afcb18e1fae27f3b68a25c7a151569159a9e/src/medley/core.cljc#L94-L99
(defn map-kv
  "Maps a function over the key/value pairs of an associative collection. Expects
  a function that takes two arguments, the key and value, and returns the new
  key and value as a collection of two elements."
  [f coll]
  (reduce-map (fn [xf] (fn [m k v] (let [[k v] (f k v)] (xf m k v)))) coll))


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
    [amplicon-coverage-plot]]]
   )

(defn main []
  (load-plates-by-run)
  (rdom/render [root] (js/document.getElementById "app")))

(set! (.-onload js/window) main)

(comment
  (js/console.log "Hello, world!")
  (js/alert "Alert!")
  (map #(assoc {} :run_id (:run_id %)) (:runs @db))
  (#(assoc {} :run_id (:run_id %)) (last (:runs @db)))
  (load-qc-summary "210106_M00325_0281_000000000-G6WT2" 74)
  (defn ct-not-nil [x] (not (nil? (:qpcr_ct x))))
  (defn select-data-keys [x] (select-keys x [:qpcr_ct :genome_completeness]))
  (def data (map select-data-keys (:selected-plate-qc-summary @db)))
  (filter ct-not-nil data)
  data
  (cell-renderer-hyperlink {})
  )
