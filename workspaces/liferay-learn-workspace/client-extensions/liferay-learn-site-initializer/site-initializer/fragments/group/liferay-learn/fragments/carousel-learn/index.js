
const nextButton = document.querySelector('.carousel-nav-button-next');
const prevButton = document.querySelector('.carousel-nav-button-prev');
const swiperSlideList = Array.from(document.getElementsByClassName('swiper-slide'));

let isAnimating = false;

const swiper = new Swiper('.swiper', {
	allowTouchMove: false,
	breakpoints: {
		1440: {
			slidesPerView: 1.1,
			spaceBetween: 16,
			centeredSlides: true
		},
		1024: {
			slidesPerView: 1.1,
			spaceBetween: 16,
			centeredSlides: true
		},
		0: {
			slidesPerView: 1,
			spaceBetween: 0,
			centeredSlides: true,
			allowTouchMove: true
		}
	},
	initialSlide: 1,
	loop: true,
	navigation: {
		nextEl: '.carousel-nav-button-next',
		prevEl: '.carousel-nav-button-prev',
	},
	pagination: {
		el: '.carousel-nav-container-indicators',
		clickable: true,
		type: 'bullets',
	},
});


const moveNext = () => {
	if (isAnimating) return;
	isAnimating = true;
	navigationNext(swiper);
};

const movePrev = () => {
	if (isAnimating) return;
	isAnimating = true;
	navigationPrev(swiper);
};

if (nextButton) nextButton.addEventListener('click', moveNext);
if (prevButton) prevButton.addEventListener('click', movePrev);

swiper.on('slideChangeTransitionEnd', () => {
	isAnimating = false;
});