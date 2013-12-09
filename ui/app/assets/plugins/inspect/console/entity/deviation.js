define(['text!./deviation.html', 'core/pluginapi', './../widget', './../format'], function(template, api, ConsoleWidget, formatter) {
    var ko = api.ko;
    return api.Class(ConsoleWidget, {
        id: 'console-deviation-widget',
        template: template,
        init: function(args) {
            var self = this;
            this.traceId = "unknown";
            this.parameters = function(params) {
                this.deviationType(params[0]);
                this.traceId = params[1];
            };
            this.deviationType = ko.observable();
            this.deviationTime = ko.observable();
            this.deviationActorPath = ko.observable()
            this.deviationReason = ko.observable();
            this.showSystemMessages = ko.observable(false);
            this.showNanoSeconds = ko.observable(false);
            this.showActorSystems = ko.observable(false);
            this.showTraceInformation = ko.observable(false);
            this.jsonData = ko.observable({ 'deviation': [] });

            // The events are calculated below so we can act upon showing/hiding information.
            // Building HTML dynamically like below is done since it is more straightforward than achieving
            // the same with a recursive template approach in KO.
            this.events = ko.computed(function() {
                this.isSystemEvent = function(type) {
                    if (type.indexOf('Msg') > 0 || type.indexOf('Stream') > 0) {
                        return 1;
                    } else {
                        return 0;
                    }
                }

                this.extractTrace =function(trace) {
                    if (trace == undefined) {
                        return "N/A";
                    }

                    return trace.substring(trace.lastIndexOf("/") + 1);
                }

                this.parseEvent = function(level, event, result) {
                    // check if there is an event and that we should show this event, i.e. it's not a system event
                    // when we want to hide such events.
                    if (event != undefined && !(!self.showSystemMessages() && this.isSystemEvent(event.annotation.type))) {
                        var message = undefined;
                        var reason = undefined;
                        var actorPath = undefined;
                        if (event.annotation.message != undefined) {
                            message = event.annotation.message;
                        };
                        if (event.annotation.reason != undefined) {
                            reason = event.annotation.reason;
                        }
                        if (event.annotation.actorInfo != undefined) {
                            actorPath = event.annotation.actorInfo.actorPath;
                        }
                        result.push("<div class=\"type\">" + event.annotation.type + "</div>");
                        var time = formatter.formatTime(new Date(event.timestamp));
                        result.push("<div><span class=\"label\">Time</span><span class=\"value\">" + time + "</span></div>");
                        if(self.showNanoSeconds()) {
                            result.push("<div><span class=\"label\">Nano</span><span class=\"value\">" + event.nanoTime + "</span></div>");
                        }
                        if (self.showActorSystems()) {
                            result.push("<div><span class=\"label\">Actor System</span><span class=\"value\">" + event.actorSystem + "</span></div>");
                        }
                        if (self.showTraceInformation()) {
                            result.push("<div><span class=\"label\">Parent trace id</span><span class=\"value\">" + this.extractTrace(event.parent) + "</span></div>");
                            result.push("<div><span class=\"label\">Unique trace id</span><span class=\"value\">" + this.extractTrace(event.id) + "</span></div>");
                            result.push("<div><span class=\"label\">Common trace id</span><span class=\"value\">" + this.extractTrace(event.trace) + "</span></div>");
                        }
                        if (actorPath != undefined) result.push("<div><span class=\"label\">Actor</span><span class=\"value\">" + actorPath + "</span></div>");
                        if (message != undefined) result.push("<div><span class=\"label\">Message</span><span class=\"value\">" + message + "</span></div>");
                        if (reason != undefined) result.push("<div><span class=\"label\">Reason</span><span class=\"value\">" + reason + "></span></div>");

                        // Check if this is the reason of the deviation and if so update the labels
                        if (event.annotation.reason != undefined) {
                            self.deviationTime(formatter.formatTime(new Date(event.timestamp)));
                            self.deviationActorPath(event.annotation.actorInfo.actorPath);
                            self.deviationReason(event.annotation.reason);
                        }
                    }
                }

                this.isErrorEvent = function(event) {
                    if (event != undefined
                        && event.annotation.reason != undefined) {
                        return true;
                    }

                    return false;
                }

                this.parse = function(level, collection, result) {
                    if (level == 0) {
                        result.push("<div style=\"padding-left: 20px;\">");
                    } else {
                        if (this.isErrorEvent(collection.event)) {
                            result.push("<div style=\"padding-left: 20px; border-left: 5px solid #E25758;\">");
                        } else {
                            result.push("<div style=\"padding-left: 20px; border-left: 5px solid #8BA1B0;\">");
                        }
                    }

                    this.parseEvent(level, collection.event, result);
                    if (collection.children != undefined && collection.children.length > 0) {
                        for (var i = 0; i < collection.children.length; i++) {
                            this.parse(level + 1, collection.children[i], result);
                        }
                    }
                    result.push("</div>");
                    return result;
                };

                var result = this.parse(0, self.jsonData().deviation, []);
                var html = "<div>";
                for (var i = 0; i < result.length; i++) {
                    html += result[i];
                }
                html += "</div>";
                return html;
            });
        },
        dataName: 'deviation',
        dataTypes: ['deviation'],
        dataScope: {},
        dataRequest: function() {
            return { 'traceId': this.traceId };
        },
        onData: function(data) {
            this.jsonData(data);
        }
    });
});
