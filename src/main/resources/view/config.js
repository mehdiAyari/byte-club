import { FightModule, FightOverlayModule, viewerOptions } from './modules/FightModule.js';
import { EndScreenModule } from './endscreen-module/EndScreenModule.js';

// FightOverlayModule comes after the end screen on purpose: it draws the result line above the end screen's veil.
export const modules = [FightModule, EndScreenModule, FightOverlayModule];

export const playerColors = ['#e8463c', '#3c7de8'];

export const gameName = 'Byte Club';

export const stepByStepAnimateSpeed = 3;

// Gear menu of the player.
export const options = [{
  title: 'SHAPES',
  get: function () {
    return viewerOptions.shapes;
  },
  set: function (value) {
    viewerOptions.set('shapes', value);
  },
  values: {
    'ON': true,
    'OFF': false
  }
}];
