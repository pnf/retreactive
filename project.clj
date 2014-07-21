(defproject acyclic/retreactive "0.1.0-SNAPSHOT"
  :author "Peter Fraenkel <http://podsnap.com>"
  :description "Persistent reactivity"
  :url "http://github.com/pnf/retreactive"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["sonatype" {:url "https://oss.sonatype.org/content/repositories/snapshots"
                               :update :always}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [digest "1.4.4"]
                 [clj-time "0.7.0"]
                 [amazonica "0.2.16"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
;                 [com.datomic/datomic-pro "0.9.4766.11" :exclusions [[org.slf4j/log4j-over-slf4j]]]
                 [com.datomic/datomic-free "0.9.4755"]
                 [com.taoensso/timbre "3.2.0"]
                 [acyclic/utils "0.1.0-SNAPSHOT"]
                 [acyclic/awstools "0.1.0-SNAPSHOT"]
                 [acyclic/girder "0.1.0-SNAPSHOT"]
                 [com.taoensso/carmine "2.7.0-RC1"]]

;  :aot [acyclic.retreactive.testutils.core]

  :jvm-opts  ^:replace ["-Xmx1g" "-server" ] 

  :uberjar-name "retreactve.jar"
)


  
  



