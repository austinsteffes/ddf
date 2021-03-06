/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global define*/
define([
  'marionette',
  './upload.hbs',
  'js/CustomElements',
  'component/content/upload/content.upload.view',
  'component/upload/upload',
], function(
  Marionette,
  template,
  CustomElements,
  uploadContentView,
  uploadInstance
) {
  return Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register('upload'),
    regions: {
      uploadDetails: '.upload-details',
    },
    onFirstRender: function() {
      this.listenTo(uploadInstance, 'change:currentUpload', this.onBeforeShow)
    },
    onBeforeShow: function() {
      if (uploadInstance.get('currentUpload')) {
        this.showSubViews()
      }
    },
    showSubViews: function() {
      this.uploadDetails.show(new uploadContentView())
    },
  })
})
