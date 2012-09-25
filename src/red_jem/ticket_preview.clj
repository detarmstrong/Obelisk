(in-ns 'red-jem.core)




; build ticket preview frame
; list multiple tickets found in text highlight, indenting for nested tickets

;(def ticket-preview-dialog
;  (custom-dialog
;    :id :ticket-preview-dialog-id
;    :title "Preview Tickets"
;    :parent red-jem-frame
;    :modal? true
;    :resizable? true
;    :content (mig-panel
;               :items [["Preview Tickets"]
;                       [(xyz-panel
;                          :paint it)]])))