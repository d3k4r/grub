(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1909"]
                 [core.async "0.1.0-SNAPSHOT"]
                 [http-kit "2.1.8"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.2.0"]
                 [ring/ring-core "1.2.0"]
                 [hiccup "1.0.4"]
                 [prismatic/dommy "0.1.1"]
                 [com.novemberain/monger "1.5.0"]]
  :profiles {:dev {:dependencies [[speclj "2.5.0"]
                                  [clj-webdriver "0.6.0"]]}}
  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-ring "0.8.6"]
            [speclj "2.5.0"]]
  :cljsbuild {:builds {:dev {:source-paths ["src/cljs"]
                             :compiler {:output-dir "out"
                                        :output-to "public/js/grub_dev.js"
                                        :source-map "public/js/grub_dev.js.map"
                                        :optimizations :whitespace
                                        :pretty-print false}}
                       :prod {:source-paths ["src/cljs"]
                              :compiler {:output-to "public/js/grub.js"
                                         :optimizations :simple}}}}
  :source-paths ["src/clj" "integration"]
  :test-paths ["spec/clj"]
  :ring {:handler grub.core/app}
  :main grub.core)
