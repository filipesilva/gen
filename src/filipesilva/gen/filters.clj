(ns filipesilva.gen.filters
  (:require [camel-snake-kebab.core :as csk]
            [selmer.filters :as sf]))

(sf/add-filter! :pascal-case csk/->PascalCase)
(sf/add-filter! :camel-case csk/->camelCase)
(sf/add-filter! :screaming-snake-case csk/->SCREAMING_SNAKE_CASE)
(sf/add-filter! :snake-case csk/->snake_case)
(sf/add-filter! :kebab-case csk/->kebab-case)
(sf/add-filter! :camel-snake-case csk/->Camel_Snake_Case)
(sf/add-filter! :http-header-case csk/->HTTP-Header-Case)
