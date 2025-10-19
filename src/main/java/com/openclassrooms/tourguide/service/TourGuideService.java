package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionsDto;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import org.springframework.web.bind.annotation.RequestParam;
import tripPricer.Provider;
import tripPricer.TripPricer;

import static java.util.stream.Collectors.toList;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final double EarthSemiCircle = 6371;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/*
	* pour trouver la position du user
	 */
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	//version bonne pour le test
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		Location userLoc = visitedLocation.location;
		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(a -> getDistance(userLoc, a)))
				.limit(5)  // les 5 plus proches
				.collect(Collectors.toList());
	}

	public List<NearbyAttractionsDto> getNearbyAttractionsDto(VisitedLocation visitedLocation, User user) {
		Location userLoc = visitedLocation.location;

		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(a -> getDistance(userLoc, new Location(a.latitude, a.longitude))))
				.limit(5)
				.map(a -> new NearbyAttractionsDto(
						a.attractionName,
						a.latitude,
						a.longitude,
						userLoc.latitude,
						userLoc.longitude,
						getDistance(userLoc, new Location(a.latitude, a.longitude)),
						rewardsService.getRewardPoints(a, user)
				))
				.toList();
	}


	//pour calculer la distance entre le user et attraction, calculer la distance entre 2 points
	private double getDistance(Location Loc1,Location Loc2) {

		double lat1 = Math.toRadians(Loc1.latitude);
		double lat2 = Math.toRadians(Loc2.latitude);
		double long1 = Math.toRadians(Loc1.longitude);
		double long2 = Math.toRadians(Loc2.longitude);

		double deriveeLat = lat2 - lat1;
		double deriveeLon = long2 - long1;

		//application du théorème de pythagore (distance en radians)
		double distanceRadians = Math.sqrt(deriveeLat * deriveeLat + deriveeLon * deriveeLon);
		//pour avoir une distance en km on doit * par le rayon de la Terre (6371 kms

		return EarthSemiCircle*distanceRadians;

	}

	/*
	
		//calculer la distance entre le user te attraction
	private double distanceInKms(Location userLoc,Attraction attraction) {
		return getDistance(userLoc, new Location(attraction.latitude, attraction.longitude));
	}
	
        public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
            List<Attraction> nearbyAttractions = new ArrayList<>();
            for (Attraction attraction : gpsUtil.getAttractions()) {
                if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
                    nearbyAttractions.add(attraction);
                }
            }

            return nearbyAttractions;
        }

	public List<NearbyAttractionsDto> getNearByAttractions(VisitedLocation visitedLocation, User user) {
		//Si l'utilisateur n'est aps nulle alors choisi entre la dernière location visitée et la location actuelle
		VisitedLocation visited = (user.getVisitedLocations().size() >0)?user.getLastVisitedLocation():trackUserLocation(user);

		Location userLoc = visited.location; //on crée une variable userLoc qui récupère laposition du user;

		//On commence notre stream en disant qu'on ne prendra que les 5 premieres attractions dasn la liste
		// qui est trié en fct de la distance
		return gpsUtil.getAttractions().stream().limit(5)
				.sorted(Comparator.comparingDouble(a -> distanceInKms(userLoc, a)))
				.map(a -> new NearbyAttractionsDto(
						a.attractionName,
						a.latitude,
						a.longitude,
						userLoc.latitude, userLoc.longitude, distanceInKms(userLoc, a),
						rewardsService.getRewardPoints(a, user)
				)).toList();
	}
*/



	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
	