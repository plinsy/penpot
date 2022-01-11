;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.components.color-input
  (:require
   [app.util.color :as uc]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [app.util.keyboard :as kbd]
   [app.util.object :as obj]
   [rumext.alpha :as mf]))

(defn clean-color
  [value]
  (-> value
      (uc/expand-hex)
      (uc/parse-color)
      (uc/prepend-hash)))

(mf/defc color-input
  {::mf/wrap-props false
   ::mf/forward-ref true}
  [props external-ref]
  (let [value     (obj/get props "value")
        on-change (obj/get props "onChange")

        ;; We need a ref pointing to the input dom element, but the user
        ;; of this component may provide one (that is forwarded here).
        ;; So we use the external ref if provided, and the local one if not.
        local-ref (mf/use-ref)
        ref       (or external-ref local-ref)

        ;; We need to store the handle-blur ref so we can call it on unmount
        handle-blur-ref (mf/use-ref nil)
        dirty-ref (mf/use-ref false)

        parse-value
        (mf/use-callback
         (mf/deps ref)
         (fn []
           (let [input-node (mf/ref-val ref)]
             (try
               (let [new-value (clean-color (dom/get-value input-node))]
                 (dom/set-validity! input-node "")
                 new-value)
               (catch :default _e
                 (dom/set-validity! input-node (tr "errors.invalid-color"))
                 nil)))))

        update-input
        (mf/use-callback
         (mf/deps ref)
         (fn [new-value]
           (let [input-node (mf/ref-val ref)]
             (dom/set-value! input-node (uc/remove-hash new-value)))))

        apply-value
        (mf/use-callback
         (mf/deps on-change update-input)
         (fn [new-value]
           (mf/set-ref-val! dirty-ref false)
           (when (and new-value (not= (uc/remove-hash new-value) value))
             (when on-change
               (on-change new-value))
             (update-input new-value))))

        handle-key-down
        (mf/use-callback
         (mf/deps apply-value update-input)
         (fn [event]
           (mf/set-ref-val! dirty-ref true)
           (let [enter? (kbd/enter? event)
                 esc?   (kbd/esc? event)]
             (when enter?
               (dom/prevent-default event)
               (let [new-value (parse-value)]
                 (apply-value new-value)))
             (when esc?
               (dom/prevent-default event)
               (update-input value)))))

        handle-blur
        (mf/use-callback
         (mf/deps parse-value apply-value update-input)
         (fn [_]
           (let [new-value (parse-value)]
             (if new-value
               (apply-value new-value)
               (update-input value)))))

        ;; list-id (str "colors-" (uuid/next))

        props (-> props
                  (obj/without ["value" "onChange"])
                  (obj/set! "type" "text")
                  (obj/set! "ref" ref)
                  ;; (obj/set! "list" list-id)
                  (obj/set! "defaultValue" value)
                  (obj/set! "onKeyDown" handle-key-down)
                  (obj/set! "onBlur" handle-blur))]

    (mf/use-effect
     (mf/deps value)
     (fn []
       (when-let [node (mf/ref-val ref)]
         (dom/set-value! node value))))

    (mf/use-effect
     (mf/deps handle-blur)
     (fn []
       (mf/set-ref-val! handle-blur-ref {:fn handle-blur})))

    (mf/use-layout-effect
     (fn []
       #(when (mf/ref-val dirty-ref)
          (let [handle-blur (:fn (mf/ref-val handle-blur-ref))]
            (handle-blur)))))

    [:*
     [:> :input props]
     ;; FIXME: this causes some weird interactions because of using apply-value
     ;; [:datalist {:id list-id}
     ;;  (for [color-name uc/color-names]
     ;;    [:option color-name])]
     ]))

