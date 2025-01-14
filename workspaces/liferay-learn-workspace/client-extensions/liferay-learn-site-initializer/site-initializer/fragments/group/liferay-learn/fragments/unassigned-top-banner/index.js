/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
if (!themeDisplay.isSignedIn()) {
	document.addEventListener('DOMContentLoaded', () => {
		const iconX = document.querySelector('.icon-x');
		const bannerSignIn = document.querySelector('.banner-sign-in');
		const navMenu = document.querySelector(
			'.has-control-menu nav.public-sites-navigation'
		);
		const navPublicSitesNavigation = document.querySelector(
			'nav.public-sites-navigation'
		);
		const learnNavBar = document.querySelector('.learn-nav-bar');

		iconX.addEventListener('click', (event) => {
			bannerSignIn.style.display = 'none';
			navPublicSitesNavigation.style.top = '0';
			learnNavBar.style.height = '20px';
		});
	});
}
