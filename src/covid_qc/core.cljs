(ns covid-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ag-grid-react :as ag-grid]
            [ag-charts-react :as ag-chart]))

(defonce db (r/atom {}))


(defn load-plates-by-run []
  (go (let [response (<! (http/get "/data/plates_by_run.json"))]
        (swap! db assoc-in [:runs] (:body response)))))

(defn load-qc-summary [run-id plate-id]
  (go (let [filename (str run-id "_" plate-id "_summary_qc.json")
            path (str "/data/ncov-tools-summary/" filename)
            response (<! (http/get path))]
        (if (= 200 (:status response))
          (swap! db assoc-in [:selected-plate-qc-summary] (:body response))))))

(defn header []
  [:header {:style {:display "grid"
                    :grid-template-columns "repeat(2, 1fr)"}}
   [:div
    [:h1 {:style {:font-family "Arial" :color "#004a87"}} "COVID-19 Genomics QC"]]
   [:div {:style {:display "grid" :align-self "center" :justify-self "end"}}
    [:img {:src "images/bccdc_logo.svg" :height "72px"}]]])


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

(defn expand-run [run]
  (let [run-id (:run_id run)
        plate-ids (:plate_ids run)]
    (map #(assoc {:run_id run-id} :plate_id %) plate-ids)))

(defn get-plates-for-run-id [run-id]
  (mapcat expand-run (filter #(= run-id (:run_id %)) (:runs @db))))

(defn runs-table []
  (let [row-data (map #(assoc {} :run_id (:run_id %)) (:runs @db))]
    [:div {:class "ag-theme-balham"
           :style {:height 300}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "multiple"
       :onFirstDataRendered #(. (. % -api) sizeColumnsToFit)
       :onSelectionChanged #(swap! db assoc-in [:selected-runs] (get-selected-rows %))}
      [:> ag-grid/AgGridColumn {:field "run_id" :headerName "Run ID" :resizable true :filter "agTextColumnFilter" :sortable true :checkboxSelection true :headerCheckboxSelection true}]]]))


(defn plates-table []
  (let [row-data (mapcat get-plates-for-run-id (map :run_id (:selected-runs @db)))]
    [:div {:class "ag-theme-balham"
           :style {:height 300}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :rowSelection "single"
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged plate-selected
       }
      [:> ag-grid/AgGridColumn {:field "plate_id" :headerName "Plate Number" :filter "agNumberColumnFilter" :sortable true :checkboxSelection true}]]]
    ))

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
           :style {:height 300}}
     [:> ag-grid/AgGridReact
      {:rowData row-data
       :pagination false
       :floatingFilter true
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)}
      [:> ag-grid/AgGridColumn {:field "library_id" :headerName "Library ID" :maxWidth 200 :sortable true :resizable true :filter "agTextColumnFilter"}]
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

(defn root []
  [:div
   [header]
   [:div {:style {:display "grid"
                  :grid-template-columns "2fr 1fr 7fr"
                  :gap "20px"}}
    [runs-table]
    [plates-table]
    [libraries-table]]
   [:div.plots-container {:style {:display "grid"
                                  :grid-template-columns "1fr 1fr 1fr 1fr"
                                  :gap "20px"}}
    [completeness-by-ct-plot]]]
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
  )
