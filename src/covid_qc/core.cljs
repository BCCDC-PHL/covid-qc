(ns covid-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            ["ag-grid-react" :as ag]))

(defonce db (r/atom {}))

(def samples [{:sample-id "Sample-01"}
              {:sample-id "Sample-02"}
              {:sample-id "Sample-03"}
              {:sample-id "Sample-04"}
              {:sample-id "Sample-05"}
              {:sample-id "Sample-06"}])

(defn load-plates-by-run []
  (go (let [response (<! (http/get "/data/plates_by_run.json"))]
        (swap! db assoc-in [:runs] (:body response)))))

(defn header []
  [:header {:style {:display "grid"
                    :grid-template-columns "repeat(2, 1fr)"}}
   [:div
    [:h1 {:style {:font-family "Arial" :color "#004a87"}} "COVID-19 Genomics QC"]]
   [:div {:style {:display "grid" :align-self "center" :justify-self "end"}}
    [:img {:src "images/bccdc_logo.svg" :height "72px"}]]])


(defn expand-run [run]
  (let [run-id (:run_id run)
        plate-ids (:plate_ids run)]
    (map #(assoc {:run_id run-id} :plate_id %) plate-ids)))

(defn runs-table []
  (let [row-data (mapcat expand-run (:runs @db))]
    [:div {:class "ag-theme-balham"
           :style {:height 300}}
     [:> ag/AgGridReact
      {:rowData row-data
       :pagination true
       :onFirstDataRendered #(. (. % -api) sizeColumnsToFit)}
      [:> ag/AgGridColumn {:field "run_id" :headerName "Run ID" :resizable true :filter "agTextColumnFilter" :sortable true :checkboxSelection true}]
   ]]
    ))

(defn plates-table []
  (let [row-data []]
    [:div {:class "ag-theme-balham"
           :style {:height 300}}
     [:> ag/AgGridReact
      {:rowData row-data
       :pagination true
       :onFirstDataRendered #(. (. % -api) sizeColumnsToFit)}
      [:> ag/AgGridColumn {:field "plate_id" :headerName "Plate Number" :filter "agNumberColumnFilter" :sortable true}]
   ]]
    ))



(defn samples-table [row-data]
  [:div {:class "ag-theme-balham"
         :style {:height 300}}
   [:> ag/AgGridReact
    {:rowData row-data
     :pagination true}
    [:> ag/AgGridColumn {:field "sample-id"}]
    ]]
  )

(defn root []
  [:div
   [header]
   [:div {:style {:display "grid"
                  :grid-template-columns "repeat(3, 1fr)"
                  :gap "20px"}}
    [runs-table]
    [plates-table]
    [samples-table samples]
    ]]
   )

(defn main []
  (load-plates-by-run)
  (rdom/render [root] (js/document.getElementById "app")))

(set! (.-onload js/window) main)

(comment
  (js/console.log "Hello, world!")
  (js/alert "Alert!")
  )
