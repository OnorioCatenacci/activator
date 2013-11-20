/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['webjars!knockout'], function(ko) {
  var templates = {};
  // Register a template (by text) with the template engine.

  function registerTemplate(id, text) {
    templates[id] = text;
    return id;
  }
  //define a template source that simply treats the template name as its content
  ko.templateSources.stringTemplate = function(template, templates) {
    this.templateName = template;
    this.templates = templates;
  }
  // Add the API the templates use.
  ko.utils.extend(ko.templateSources.stringTemplate.prototype, {
    data: function(key, value) {
      console.log("data", key, value, this.templateName);
      this.templates._data = this.templates._data || {};
      this.templates._data[this.templateName] = this.templates._data[this.templateName] || {};
      if(arguments.length === 1) {
        return this.templates._data[this.templateName][key];
      }
      this.templates._data[this.templateName][key] = value;
    },
    text: function(value) {
      if(arguments.length === 0) {
        return this.templates[this.templateName];
      }
      this.templates[this.templateName] = value;
    }
  });


  // We add a custom binding that allows us to delegate to a view for binding things :)
  // Kinda lazy, but it can help.
  ko.bindingHandlers.customBind = {
      init: function(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
    // This will be called when the binding is first applied to an element
    var wrappedHandler = valueAccessor();
    var handler = ko.utils.unwrapObservable(wrappedHandler);
    if(handler.init) {
      handler.init(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext);
    }
      },
      update: function(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
      // This will be called when the binding is first applied to an element
    var wrappedHandler = valueAccessor();
    var handler = ko.utils.unwrapObservable(wrappedHandler);
    if(handler.update) {
      handler.update(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext);
    }
      }
  }

  function createStringTemplateEngine(templateEngine, templates) {
    templateEngine.makeTemplateSource = function(template) {
      return new ko.templateSources.stringTemplate(template, templates);
    }
    return templateEngine;
  }

  // toggle Booleans from binding
  ko.bindingHandlers.toggle = {
    init: function(element, valueAccessor, allBindings, viewModel, bindingContext) {
      var val = valueAccessor();
      console.log("??",val, val());
      element.addEventListener("click",function(){
        val(!val());
      });
    },
    update: function() {}
  };

  // Register us immediately.
  ko.setTemplateEngine(createStringTemplateEngine(new ko.nativeTemplateEngine(), templates));

  // Just pass a function in the template, to call it
  ko.bindingHandlers['call'] = {
      init: function(element, valueAccessor, allBindings, viewModel, bindingContext) {
          valueAccessor()(element, allBindings, viewModel, bindingContext);
      }
  };

  return {
    registerTemplate: registerTemplate,
    templates: templates
  };
});
