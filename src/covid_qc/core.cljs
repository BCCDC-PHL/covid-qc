(ns covid-qc.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["ag-grid-react" :as ag]))

(defonce db (r/atom {}))


(def runs [{:run-id "Run-01" :plate-num 1}
           {:run-id "Run-01" :plate-num 2}
           {:run-id "Run-02" :plate-num 3}
           {:run-id "Run-03" :plate-num 4}
           {:run-id "Run-04" :plate-num 5}
           {:run-id "Run-05" :plate-num 6}
           {:run-id "Run-05" :plate-num 7}
           {:run-id "Run-05" :plate-num 8}
           {:run-id "Run-06" :plate-num 9}])

(def samples [{:sample-id "Sample-01"}
              {:sample-id "Sample-02"}
              {:sample-id "Sample-03"}
              {:sample-id "Sample-04"}
              {:sample-id "Sample-05"}
              {:sample-id "Sample-06"}])

(defn header []
  [:header
   [:h1 "COVID QC"]])

(defn runs-table [row-data]
  [:div {:class "ag-theme-balham"
         :style {:height 500}}
  [:> ag/AgGridReact
   {:rowData row-data}
   [:> ag/AgGridColumn {:field "run-id" :sortable true :checkboxSelection true}]
   [:> ag/AgGridColumn {:field "plate-num" :sortable true}]
   ]]
  )

(defn samples-table [row-data]
  [:div {:class "ag-theme-balham"
         :style {}}
   [:> ag/AgGridReact
    {:rowData row-data}
    [:> ag/AgGridColumn {:field "sample-id"}]
    ]]
  )

(defn root []
  [:div
   [header]
   [:div {:style {:display "grid"
                  :grid-template-columns "repeat(2, 1fr)"
                  :gap "20px"}}
    [runs-table runs]
    [samples-table samples]
    ]]
   )

(rdom/render [root] (js/document.getElementById "app"))

(comment
  (js/console.log "Hello, world!")
  (js/alert "Alert!")
  )
