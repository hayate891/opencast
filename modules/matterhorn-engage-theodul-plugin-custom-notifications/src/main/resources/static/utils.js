/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
/*jslint browser: true, nomen: true*/
/*global define, CustomEvent*/
define(["jquery", "moment"], function($, Moment) {
    "use strict";

    function Utils() {
        // nothing to see here
    }

    /**
     * Format the current date and time
     *
     * @return a formatted current date and time string
     */
    Utils.prototype.getCurrentDateTime = function(locale) {
        var date = new Date();
        Moment.locale(locale, {
            // customizations
        });

        // try to format the date
        if (Moment(date) != null) {
            date = Moment(new Date()).format(dateFormat);
        }
        return date;
    }

    return Utils;
});
